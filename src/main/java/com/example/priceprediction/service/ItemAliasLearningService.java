package com.example.priceprediction.service;

import com.example.priceprediction.entity.CsQaqItemIdEntity;
import com.example.priceprediction.entity.ItemAliasMappingEntity;
import com.example.priceprediction.rag.EmbeddingClient;
import com.example.priceprediction.rag.VectorStoreClient;
import com.example.priceprediction.repository.ItemAliasMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ItemAliasLearningService {

    private static final String PENDING_ALIAS_PREFIX = "item_alias:pending:";
    private static final Duration PENDING_ALIAS_TTL = Duration.ofMinutes(30);
    private static final Pattern NOISE_WORDS = Pattern.compile(
            "(能不能|值不值得|值得|能买吗|能买|买吗|买不买|最近|现在|行情|价格|走势|分析|看看|看下|一下|这个|这把|饰品|皮肤|还|吗|呢|啊|\\?|？)",
            Pattern.CASE_INSENSITIVE
    );

    private final ItemAliasMappingRepository itemAliasMappingRepository;
    private final StringRedisTemplate redisTemplate;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;

    public ItemAliasLearningService(
            ItemAliasMappingRepository itemAliasMappingRepository,
            StringRedisTemplate redisTemplate,
            EmbeddingClient embeddingClient,
            VectorStoreClient vectorStoreClient
    ) {
        this.itemAliasMappingRepository = itemAliasMappingRepository;
        this.redisTemplate = redisTemplate;
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
    }

    @Transactional
    public Optional<ItemAliasMappingEntity> findAliasForQuery(String query) {
        String normalizedQuery = normalizeAlias(extractAliasCandidate(query));
        if (!StringUtils.hasText(normalizedQuery)) {
            return Optional.empty();
        }

        Optional<ItemAliasMappingEntity> exact = itemAliasMappingRepository
                .findByNormalizedAlias(normalizedQuery)
                .filter(this::isActive);
        if (exact.isPresent()) {
            increaseHitCount(exact.get());
            return exact;
        }

        List<ItemAliasMappingEntity> activeAliases =
                itemAliasMappingRepository.findByStatus(ItemAliasMappingEntity.STATUS_ACTIVE);

        Optional<ItemAliasMappingEntity> fuzzy = activeAliases.stream()
                .filter(alias -> StringUtils.hasText(alias.getNormalizedAlias()))
                .filter(alias -> normalizedQuery.contains(alias.getNormalizedAlias())
                        || alias.getNormalizedAlias().contains(normalizedQuery))
                .max(Comparator
                        .comparingInt((ItemAliasMappingEntity alias) -> alias.getNormalizedAlias().length())
                        .thenComparing(alias -> alias.getHitCount() == null ? 0L : alias.getHitCount()));

        fuzzy.ifPresent(this::increaseHitCount);
        return fuzzy;
    }

    public void savePendingAlias(String memoryId, String query) {
        if (!StringUtils.hasText(memoryId) || !StringUtils.hasText(query)) {
            return;
        }

        String alias = extractAliasCandidate(query);
        if (!StringUtils.hasText(alias)) {
            return;
        }

        redisTemplate.opsForValue().set(pendingAliasKey(memoryId), alias, PENDING_ALIAS_TTL);
    }

    public Optional<String> getPendingAlias(String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(redisTemplate.opsForValue().get(pendingAliasKey(memoryId)))
                .filter(StringUtils::hasText);
    }

    public void clearPendingAlias(String memoryId) {
        if (StringUtils.hasText(memoryId)) {
            redisTemplate.delete(pendingAliasKey(memoryId));
        }
    }

    @Transactional
    public Optional<ItemAliasMappingEntity> learnAlias(String alias, CsQaqItemIdEntity item) {
        if (!StringUtils.hasText(alias) || item == null || item.getItemId() == null) {
            return Optional.empty();
        }

        String normalizedAlias = normalizeAlias(alias);
        if (!StringUtils.hasText(normalizedAlias)) {
            return Optional.empty();
        }

        ItemAliasMappingEntity entity = itemAliasMappingRepository
                .findByNormalizedAlias(normalizedAlias)
                .orElseGet(ItemAliasMappingEntity::new);

        if (entity.getId() != null
                && entity.getItemId() != null
                && !entity.getItemId().equals(item.getItemId())) {
            entity.setStatus(ItemAliasMappingEntity.STATUS_CONFLICT);
            itemAliasMappingRepository.save(entity);
            return Optional.empty();
        }

        entity.setAlias(alias.trim());
        entity.setNormalizedAlias(normalizedAlias);
        entity.setItemId(item.getItemId());
        entity.setCnName(item.getCnName());
        entity.setMarketHashName(item.getMarketHashName());
        entity.setSource(ItemAliasMappingEntity.SOURCE_USER_CONFIRMED);
        entity.setStatus(ItemAliasMappingEntity.STATUS_ACTIVE);
        entity.setConfidence(1.0);
        entity.setHitCount(entity.getHitCount() == null ? 1L : entity.getHitCount() + 1L);

        ItemAliasMappingEntity saved = itemAliasMappingRepository.save(entity);
        try {
            upsertAliasVector(saved);
        } catch (Exception e) {
            log.warn(
                    "Alias was saved to MySQL but failed to upsert Qdrant vector, alias={}, itemId={}",
                    saved.getAlias(),
                    saved.getItemId(),
                    e
            );
        }
        return Optional.of(saved);
    }

    public String extractAliasCandidate(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }

        String alias = query.trim();
        alias = NOISE_WORDS.matcher(alias).replaceAll(" ");
        alias = alias.replaceAll("[,，。.!！:：;；\\s]+", " ").trim();
        return alias;
    }

    public String normalizeAlias(String alias) {
        if (!StringUtils.hasText(alias)) {
            return "";
        }
        return alias.toLowerCase()
                .replaceAll("[\\s,，。.!！:：;；|｜()（）\\[\\]【】{}]+", "")
                .trim();
    }

    private void increaseHitCount(ItemAliasMappingEntity entity) {
        entity.setHitCount(entity.getHitCount() == null ? 1L : entity.getHitCount() + 1L);
        itemAliasMappingRepository.save(entity);
    }

    private boolean isActive(ItemAliasMappingEntity entity) {
        return entity != null && ItemAliasMappingEntity.STATUS_ACTIVE.equals(entity.getStatus());
    }

    private void upsertAliasVector(ItemAliasMappingEntity entity) {
        String content = """
                用户别名：%s
                标准饰品中文名：%s
                标准 Steam market_hash_name：%s
                用户说“%s”时，通常指 %s。
                """.formatted(
                entity.getAlias(),
                entity.getCnName(),
                entity.getMarketHashName(),
                entity.getAlias(),
                entity.getMarketHashName()
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("doc_type", "ITEM_ALIAS");
        metadata.put("alias", entity.getAlias());
        metadata.put("normalized_alias", entity.getNormalizedAlias());
        metadata.put("target_item_id", entity.getItemId());
        metadata.put("target_cn_name", entity.getCnName());
        metadata.put("target_market_hash_name", entity.getMarketHashName());
        metadata.put("source", entity.getSource());
        metadata.put("confidence", entity.getConfidence());
        metadata.put("item_id", entity.getItemId());
        metadata.put("name", entity.getCnName());
        metadata.put("market_hash_name", entity.getMarketHashName());

        String docId = "ITEM_ALIAS:" + entity.getNormalizedAlias() + ":" + entity.getItemId();
        vectorStoreClient.upsert(new VectorStoreClient.VectorRecord(
                docId,
                embeddingClient.embed(content),
                content,
                metadata
        ));
    }

    private String pendingAliasKey(String memoryId) {
        return PENDING_ALIAS_PREFIX + memoryId;
    }
}
