package com.example.priceprediction.service;

import com.example.priceprediction.dto.InventoryItemDTO;
import com.example.priceprediction.entity.InventoryItemEntity;
import com.example.priceprediction.repository.InventoryItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InventoryService {

    @Autowired
    private RestTemplate restTemplate;

    // 引入刚刚建好的 Repository
    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String INVENTORY_REFRESH_TOPIC = "inventory-refresh-topic";
    private static final int STEAM_INVENTORY_PAGE_SIZE = 500;
    private static final int MAX_STEAM_INVENTORY_PAGES = 200;

    /**
     * 手动刷新：发送消息到 Kafka，异步处理库存刷新
     */
    public boolean refreshInventory(String steamId) {
        String rateLimitKey = "inventory_refresh_rate_limit:" + steamId;

        Boolean locker = redisTemplate.opsForValue().setIfAbsent(rateLimitKey, "1", Duration.ofSeconds(60));
        if (Boolean.FALSE.equals(locker)) {
            log.info("库存刷新任务已存在，拒绝重复请求, SteamID: {}", steamId);
            return false; // 已有任务在执行，拒绝新的请求
        }

        Map<String, Object> message = new HashMap<>();
        String requestId = UUID.randomUUID().toString();
        message.put("requestId", requestId);
        message.put("steamId", steamId);
        message.put("timestamp", System.currentTimeMillis());
        // 使用 steamId 作为消息 key，保证同一用户的消息落到同一分区，便于顺序处理
        kafkaTemplate.send(INVENTORY_REFRESH_TOPIC, steamId, message);
        log.info("Inventory refresh message sent to Kafka, SteamID: {}, requestId: {}", steamId, requestId);
        return true;
    }

    /**
     * 执行库存刷新逻辑（由消费者调用）
     */
    public void doRefreshInventory(String steamId) {
        try {
            log.info("开始向 Steam 分页请求最新库存, SteamID: {}", steamId);

            List<InventoryItemDTO> mergedDtoList = new ArrayList<>();
            Set<String> seenLastAssetId = new HashSet<>();
            String nextStartAssetId = null;
            boolean explicitEmptyInventory = false;
            int fetchedPages = 0;

            while (true) {
                if (fetchedPages >= MAX_STEAM_INVENTORY_PAGES) {
                    log.warn("用户 {} 库存分页超过安全上限 {} 页，保留旧库存", steamId, MAX_STEAM_INVENTORY_PAGES);
                    return;
                }
                fetchedPages++;
                String pageUrl = buildInventoryUrl(steamId, nextStartAssetId);
                String response = restTemplate.getForObject(pageUrl, String.class);

                if (response == null || response.isBlank()) {
                    log.warn("用户 {} 第 {} 页库存响应为空，保留旧库存", steamId, fetchedPages);
                    return;
                }

                JsonNode root = objectMapper.readTree(response);

                if (root.path("success").asInt() != 1) {
                    log.warn("用户 {} 第 {} 页库存读取失败，可能是隐私设置未公开或被限流", steamId, fetchedPages);
                    return;
                }

                JsonNode assetsNode = root.path("assets");
                if (!assetsNode.isArray()) {
                    log.warn("用户 {} 第 {} 页库存响应缺少有效的 assets 数组，保留旧库存", steamId, fetchedPages);
                    return;
                }

                if (fetchedPages == 1 && assetsNode.isEmpty()) {
                    explicitEmptyInventory = true;
                    break;
                }

                List<InventoryItemDTO> pageDtoList = parseInventoryJson(response);
                if (!pageDtoList.isEmpty()) {
                    mergedDtoList.addAll(pageDtoList);
                }

                boolean moreItems = root.path("more_items").asBoolean(false);
                if (!moreItems) {
                    break;
                }

                String lastAssetId = root.path("last_assetid").asText("");
                if (lastAssetId.isEmpty()) {
                    log.warn("用户 {} 第 {} 页返回 more_items=true 但缺少 last_assetid，保留旧库存", steamId, fetchedPages);
                    return;
                }
                if (!seenLastAssetId.add(lastAssetId)) {
                    log.warn("用户 {} 检测到重复 last_assetid={}，可能陷入分页循环，保留旧库存", steamId, lastAssetId);
                    return;
                }

                nextStartAssetId = lastAssetId;
            }

            if (explicitEmptyInventory) {
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.executeWithoutResult(status -> {
                    log.info("用户 {} 库存为空，清空数据库旧记录", steamId);
                    inventoryItemRepository.deleteAllBySteamId(steamId);
                });
                return;
            }

            // 合并分页后按 assetId 去重，确保后续只做一次统一差异更新
            Map<String, InventoryItemDTO> deduplicatedMap = new LinkedHashMap<>();
            for (InventoryItemDTO dto : mergedDtoList) {
                if (dto.getAssetId() != null && !dto.getAssetId().isEmpty()) {
                    deduplicatedMap.put(dto.getAssetId(), dto);
                }
            }
            List<InventoryItemDTO> dtoList = new ArrayList<>(deduplicatedMap.values());

            if (dtoList.isEmpty()) {
                log.warn("用户 {} 库存解析结果为空，但响应并未明确确认为空库存，保留旧库存", steamId);
                return;
            }

            log.info("用户 {} 库存分页拉取完成，共 {} 页，解析后 {} 件饰品", steamId, fetchedPages, dtoList.size());

            // 仅将数据库写入放入事务，HTTP 请求和 JSON 解析都在事务外执行
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                // 将 DTO 转换为数据库 Entity，并在写入前做变更判断：仅对差异部分进行 delete/insert/update，避免不必要的全量写入
                List<InventoryItemEntity> existingEntities = inventoryItemRepository.findBySteamId(steamId);

                Set<String> existingIds = existingEntities.stream()
                        .map(InventoryItemEntity::getAssetId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                Map<String, InventoryItemDTO> dtoMap = new HashMap<>();
                for (InventoryItemDTO dto : dtoList) {
                    dtoMap.put(dto.getAssetId(), dto);
                }
                Set<String> newIds = dtoMap.keySet();

                // 如果没有变化，直接跳过写入，节省 IO
                if (existingIds.equals(newIds)) {
                    log.info("用户 {} 库存与数据库一致，跳过写入，共 {} 件饰品", steamId, newIds.size());
                    return;
                }

                // 计算需要删除的实体（DB 中有但新数据中没有）
                List<InventoryItemEntity> toDelete = new ArrayList<>();
                for (InventoryItemEntity e : existingEntities) {
                    if (!newIds.contains(e.getAssetId())) {
                        toDelete.add(e);
                    }
                }

                // 计算需要插入的实体（新数据有但 DB 中没有）
                List<InventoryItemEntity> toInsert = new ArrayList<>();
                for (String assetId : newIds) {
                    if (!existingIds.contains(assetId)) {
                        InventoryItemDTO dto = dtoMap.get(assetId);
                        InventoryItemEntity entity = new InventoryItemEntity();
                        entity.setSteamId(steamId);
                        entity.setAssetId(dto.getAssetId());
                        entity.setName(dto.getName());
                        entity.setWear(dto.getWear());
                        entity.setAmount(dto.getAmount());
                        entity.setIconUrl(dto.getIconUrl());
                        toInsert.add(entity);
                    }
                }

                // 计算需要更新的实体（DB 中存在，但字段有变化）
                List<InventoryItemEntity> toUpdate = new ArrayList<>();
                Map<String, InventoryItemEntity> existMap = new HashMap<>();
                for (InventoryItemEntity e : existingEntities) {
                    existMap.put(e.getAssetId(), e);
                }
                for (String assetId : newIds) {
                    if (existingIds.contains(assetId)) {
                        InventoryItemDTO dto = dtoMap.get(assetId);
                        InventoryItemEntity exist = existMap.get(assetId);
                        boolean changed = false;
                        if (!Objects.equals(dto.getName(), exist.getName())) {
                            exist.setName(dto.getName()); changed = true;
                        }
                        if (!Objects.equals(dto.getWear(), exist.getWear())) {
                            exist.setWear(dto.getWear()); changed = true;
                        }
                        if (!Objects.equals(dto.getAmount(), exist.getAmount())) {
                            exist.setAmount(dto.getAmount()); changed = true;
                        }
                        if (!Objects.equals(dto.getIconUrl(), exist.getIconUrl())) {
                            exist.setIconUrl(dto.getIconUrl()); changed = true;
                        }
                        if (changed) toUpdate.add(exist);
                    }
                }

                // 执行差异化操作
                if (!toDelete.isEmpty()) {
                    inventoryItemRepository.deleteAll(toDelete);
                    log.info("用户 {} 删除 {} 件过期库存", steamId, toDelete.size());
                }
                if (!toUpdate.isEmpty()) {
                    inventoryItemRepository.saveAll(toUpdate);
                    log.info("用户 {} 更新 {} 件库存字段", steamId, toUpdate.size());
                }
                if (!toInsert.isEmpty()) {
                    inventoryItemRepository.saveAll(toInsert);
                    log.info("用户 {} 新增 {} 件库存", steamId, toInsert.size());
                }
                log.info("用户 {} 数据库库存差异化更新完成，删除 {}，更新 {}，新增 {}", steamId, toDelete.size(), toUpdate.size(), toInsert.size());
            });

        } catch (Exception e) {
            log.error("Inventory refresh failed, steamId={}", steamId, e);
            throw new IllegalStateException("Inventory refresh failed, steamId=" + steamId, e);
        }
    }

    private String buildInventoryUrl(String steamId, String startAssetId) {
        String url = String.format("https://steamcommunity.com/inventory/%s/730/2?l=zh-hans&count=%d", steamId, STEAM_INVENTORY_PAGE_SIZE);
        if (startAssetId == null || startAssetId.isEmpty()) {
            return url;
        }
        return url + "&start_assetid=" + startAssetId;
    }

    /**
     * 本地读取：从 MySQL 数据库中直接读取用户的库存快照
     * 速度极快，用于用户打开页面时的展示
     */
    public List<InventoryItemDTO> getUserInventoryFromDb(String steamId) {
        // 1. 从数据库查出 Entity 列表
        List<InventoryItemEntity> entities = inventoryItemRepository.findBySteamId(steamId);

        // 2. 将 Entity 转换回 DTO 给前端展示
        return entities.stream().map(entity -> {
            InventoryItemDTO dto = new InventoryItemDTO();
            dto.setAssetId(entity.getAssetId());
            dto.setName(entity.getName());
            dto.setWear(entity.getWear());
            dto.setAmount(entity.getAmount());
            dto.setIconUrl(entity.getIconUrl());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 核心解析逻辑 (保持不变)
     */
    private List<InventoryItemDTO> parseInventoryJson(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode assets = root.path("assets");
        JsonNode descriptions = root.path("descriptions");

        if (assets.isMissingNode() || descriptions.isMissingNode()) {
            return Collections.emptyList();
        }

        Map<String, JsonNode> descMap = new HashMap<>();
        for (JsonNode d : descriptions) {
            String key = d.get("classid").asText() + "_" + d.get("instanceid").asText();
            descMap.put(key, d);
        }

        List<InventoryItemDTO> result = new ArrayList<>();
        for (JsonNode a : assets) {
            String key = a.get("classid").asText() + "_" + a.get("instanceid").asText();
            JsonNode detail = descMap.get(key);

            if (detail != null) {
                String wear = "无磨损";
                JsonNode tags = detail.path("tags");
                if (tags.isArray()) {
                    for (JsonNode tag : tags) {
                        if ("Exterior".equals(tag.path("category").asText())) {
                            wear = tag.path("localized_tag_name").asText();
                            break;
                        }
                    }
                }

                InventoryItemDTO item = new InventoryItemDTO();
                item.setAssetId(a.path("assetid").asText());
                item.setName(detail.path("name").asText());
                item.setWear(wear);
                item.setAmount(a.path("amount").asInt(1));
                item.setIconUrl(detail.path("icon_url").asText());
                result.add(item);
            }
        }
        return result;
    }
}
