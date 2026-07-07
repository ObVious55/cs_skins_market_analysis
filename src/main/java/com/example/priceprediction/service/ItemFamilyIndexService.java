package com.example.priceprediction.service;

import com.example.priceprediction.entity.CsQaqItemIdEntity;
import com.example.priceprediction.repository.CsQaqItemIdRepository;
import com.example.priceprediction.service.ItemNameParser.ParsedItemName;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ItemFamilyIndexService {

    private static final Pattern TOKEN_SPLIT =
            Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CJK_LATIN_BOUNDARY =
            Pattern.compile("(?<=[\\p{IsHan}])(?=[a-z0-9])|(?<=[a-z0-9])(?=[\\p{IsHan}])");

    private final CsQaqItemIdRepository csQaqItemIdRepository;
    private final ItemNameParser itemNameParser;
    private volatile List<ItemFamilyDocument> familyDocuments = List.of();

    public ItemFamilyIndexService(
            CsQaqItemIdRepository csQaqItemIdRepository,
            ItemNameParser itemNameParser
    ) {
        this.csQaqItemIdRepository = csQaqItemIdRepository;
        this.itemNameParser = itemNameParser;
    }

    @PostConstruct
    public void rebuild() {
        List<CsQaqItemIdEntity> items = csQaqItemIdRepository.findAll();
        Map<String, FamilyBucket> buckets = new LinkedHashMap<>();

        for (CsQaqItemIdEntity item : items) {
            FamilyFields fields = toFamilyFields(item);
            FamilyBucket bucket = buckets.computeIfAbsent(fields.familyKey(), ignored -> new FamilyBucket(fields));
            bucket.add(item, fields);
        }

        List<ItemFamilyDocument> documents = new ArrayList<>();
        for (FamilyBucket bucket : buckets.values()) {
            documents.add(bucket.toDocument());
        }
        this.familyDocuments = List.copyOf(documents);
        System.out.println("Item family lexical index loaded: " + this.familyDocuments.size());
    }

    public List<ItemFamilyDocument> getFamilyDocuments() {
        return familyDocuments;
    }

    public List<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        for (String token : TOKEN_SPLIT.split(normalized)) {
            if (token == null || token.isBlank()) {
                continue;
            }
            addToken(tokens, token);
            addMixedAlphaNumberTokens(tokens, token);
            addCjkLatinTokens(tokens, token);
        }
        return tokens.stream().distinct().toList();
    }

    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace("（", "(")
                .replace("）", ")")
                .replace("｜", "|")
                .replace("™", "")
                .trim();
    }

    private FamilyFields toFamilyFields(CsQaqItemIdEntity item) {
        Optional<ParsedItemName> parsedOpt = itemNameParser.parse(item.getMarketHashName(), item.getCnName());
        if (parsedOpt.isPresent()) {
            ParsedItemName parsed = parsedOpt.get();
            String weapon = stripVariantPrefix(parsed.weapon());
            String skinEn = stripVariantPrefix(parsed.skinEn());
            String skinCn = stripVariantPrefix(parsed.skinCn());
            String familyKey = weapon + "|" + skinEn;
            return new FamilyFields(
                    familyKey,
                    weapon,
                    skinCn,
                    skinEn,
                    weapon + " | " + skinCn,
                    weapon + " | " + skinEn,
                    parsed.exteriorCn(),
                    parsed.exteriorEn(),
                    isStatTrak(item)
            );
        }

        String titleCn = stripVariantPrefix(stripTrailingExterior(item.getCnName()));
        String titleEn = stripVariantPrefix(stripTrailingExterior(item.getMarketHashName()));
        String familyKey = titleEn.isBlank() ? titleCn : titleEn;
        return new FamilyFields(familyKey, "", titleCn, titleEn, titleCn, titleEn, "", "", isStatTrak(item));
    }

    private List<String> buildSearchTerms(FamilyFields fields) {
        Set<String> terms = new LinkedHashSet<>();
        addTerm(terms, fields.titleCn());
        addTerm(terms, fields.titleEn());
        addTerm(terms, fields.skinCn());
        addTerm(terms, fields.skinEn());
        addTerm(terms, fields.weapon());
        addTerm(terms, fields.weapon() + fields.skinCn());
        addTerm(terms, fields.skinCn() + fields.weapon());
        addTerm(terms, compact(fields.weapon()) + fields.skinCn());
        addTerm(terms, fields.weapon() + " " + fields.skinCn());
        addTerm(terms, fields.weapon() + " " + fields.skinEn());
        for (String alias : weaponAliases(fields.weapon())) {
            addTerm(terms, alias);
            addTerm(terms, alias + fields.skinCn());
            addTerm(terms, fields.skinCn() + alias);
            addTerm(terms, alias + " " + fields.skinCn());
        }
        return terms.stream().filter(term -> !term.isBlank()).toList();
    }

    private List<String> buildTokens(List<String> searchTerms) {
        List<String> tokens = new ArrayList<>();
        for (String term : searchTerms) {
            tokens.addAll(tokenize(term));
        }
        return tokens.stream().distinct().toList();
    }

    private void addMixedAlphaNumberTokens(List<String> tokens, String token) {
        String spaced = token
                .replaceAll("(?i)([a-z]+)(\\d+)", "$1 $2")
                .replaceAll("(?i)(\\d+)([a-z]+)", "$1 $2");
        if (!spaced.equals(token)) {
            for (String part : spaced.split("\\s+")) {
                addToken(tokens, part);
            }
        }
    }

    private void addCjkLatinTokens(List<String> tokens, String token) {
        String spaced = CJK_LATIN_BOUNDARY.matcher(token).replaceAll(" ");
        if (!spaced.equals(token)) {
            for (String part : spaced.split("\\s+")) {
                addToken(tokens, part);
            }
        }
    }

    private void addToken(List<String> tokens, String token) {
        if (token != null && !token.isBlank()) {
            tokens.add(token);
        }
    }

    private List<String> weaponAliases(String weapon) {
        return switch (weapon) {
            case "AK-47" -> List.of("ak", "ak47", "ak-47");
            case "AWP" -> List.of("awp", "大狙");
            case "Desert Eagle" -> List.of("沙鹰", "deagle");
            case "Glock-18" -> List.of("格洛克", "glock", "glock18");
            case "USP-S" -> List.of("usp", "usp-s", "usp消音", "usp消音版");
            case "M4A1-S" -> List.of("m4a1", "m4a1s", "m4a1-s");
            default -> weapon == null || weapon.isBlank() ? List.of() : List.of(weapon);
        };
    }

    private void addTerm(Set<String> terms, String value) {
        if (value != null && !value.isBlank()) {
            terms.add(value.trim());
        }
    }

    private String stripVariantPrefix(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("StatTrak™", "")
                .replace("StatTrak", "")
                .replace("暗金", "")
                .trim();
    }

    private String stripTrailingExterior(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s*[（(][^（）()]+[）)]\\s*$", "").trim();
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private boolean isStatTrak(CsQaqItemIdEntity item) {
        return containsStatTrak(item.getCnName()) || containsStatTrak(item.getMarketHashName());
    }

    private boolean containsStatTrak(String value) {
        return value != null
                && (value.toLowerCase(Locale.ROOT).contains("stattrak") || value.contains("暗金"));
    }

    private int exteriorPriority(String exteriorCn, String exteriorEn) {
        String text = normalize((exteriorCn == null ? "" : exteriorCn) + " " + (exteriorEn == null ? "" : exteriorEn));
        if (text.contains("崭新") || text.contains("全新") || text.contains("factory new")) {
            return 5;
        }
        if (text.contains("略有") || text.contains("略磨") || text.contains("minimal wear")) {
            return 4;
        }
        if (text.contains("久经") || text.contains("field tested") || text.contains("field-tested")) {
            return 3;
        }
        if (text.contains("破损") || text.contains("well worn") || text.contains("well-worn")) {
            return 2;
        }
        if (text.contains("战痕") || text.contains("battle scarred") || text.contains("battle-scarred")) {
            return 1;
        }
        return 0;
    }

    private record FamilyFields(
            String familyKey,
            String weapon,
            String skinCn,
            String skinEn,
            String titleCn,
            String titleEn,
            String exteriorCn,
            String exteriorEn,
            boolean statTrak
    ) {
    }

    private class FamilyBucket {
        private final FamilyFields representative;
        private final List<Long> itemIds = new ArrayList<>();
        private Long defaultItemId;
        private int defaultPriority = Integer.MIN_VALUE;

        private FamilyBucket(FamilyFields representative) {
            this.representative = representative;
        }

        private void add(CsQaqItemIdEntity item, FamilyFields fields) {
            itemIds.add(item.getItemId());
            int priority = exteriorPriority(fields.exteriorCn(), fields.exteriorEn());
            if (!fields.statTrak()) {
                priority += 100;
            }
            if (defaultItemId == null || priority > defaultPriority) {
                defaultItemId = item.getItemId();
                defaultPriority = priority;
            }
        }

        private ItemFamilyDocument toDocument() {
            List<String> searchTerms = buildSearchTerms(representative);
            return new ItemFamilyDocument(
                    representative.familyKey(),
                    representative.titleCn(),
                    representative.titleEn(),
                    representative.weapon(),
                    representative.skinCn(),
                    representative.skinEn(),
                    defaultItemId,
                    List.copyOf(itemIds),
                    searchTerms,
                    buildTokens(searchTerms)
            );
        }
    }
}
