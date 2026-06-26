package com.example.priceprediction.rag;

import com.example.priceprediction.entity.CsQaqItemIdEntity;
import com.example.priceprediction.repository.CsQaqItemIdRepository;
import com.example.priceprediction.service.ItemNameParser;
import com.example.priceprediction.service.ItemNameParser.ParsedItemName;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ItemRagDocumentBuilder {

    private final CsQaqItemIdRepository csQaqItemIdRepository;
    private final ItemNameParser itemNameParser;

    public ItemRagDocumentBuilder(CsQaqItemIdRepository csQaqItemIdRepository,
                                  ItemNameParser itemNameParser) {
        this.csQaqItemIdRepository = csQaqItemIdRepository;
        this.itemNameParser = itemNameParser;
    }

    public List<ItemRagDocument> buildFamilyDocuments() {
        List<CsQaqItemIdEntity> items = csQaqItemIdRepository.findAll();
        Map<String, FamilyBucket> families = new LinkedHashMap<>();

        for (CsQaqItemIdEntity item : items) {
            FamilyItem familyItem = toFamilyItem(item);
            FamilyBucket bucket = families.computeIfAbsent(
                    familyItem.familyKey(),
                    ignored -> new FamilyBucket(familyItem)
            );
            bucket.add(item, familyItem);
        }

        List<ItemRagDocument> documents = new ArrayList<>();
        for (FamilyBucket bucket : families.values()) {
            documents.add(buildFamilyDocument(bucket));
        }
        return documents;
    }

    private String normalizeWeaponAlias(String weapon) {
        if (weapon == null) {
            return "";
        }

        return switch (weapon) {
            case "AK-47" -> "AK、AK47、ak";
            case "Desert Eagle" -> "沙鹰、Deagle";
            case "AWP" -> "大狙、awp";
            case "Glock-18" -> "格洛克、Glock、Glock18、格洛克 18 型";
            case "SSG 08" -> "鸟狙、SSG08";
            case "M4A1-S" -> "M4A1、M4A1S、M4A1 消音版";
            case "USP-S" -> "USP、USP 消音版";
            default -> weapon;
        };
    }

    private ItemRagDocument buildFamilyDocument(FamilyBucket bucket) {
        FamilyItem representative = bucket.representative();
        ItemRagDocument doc = new ItemRagDocument();

        doc.setDocId("QAQ_ITEM_FAMILY:" + representative.familyKey());
        doc.setDocType("QAQ_ITEM_FAMILY");
        doc.setNameIds(bucket.itemIds());
        doc.setFamilyKey(representative.familyKey());
        doc.setWeapon(representative.weapon());
        doc.setSkinCn(representative.skinCn());
        doc.setSkinEn(representative.skinEn());
        doc.setTitle(representative.titleCn());
        doc.setContent(buildFamilyContent(bucket));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("doc_type", "QAQ_ITEM_FAMILY");
        metadata.put("family_key", representative.familyKey());
        metadata.put("name", representative.titleCn());
        metadata.put("cn_name", representative.titleCn());
        metadata.put("market_hash_name", representative.titleEn());
        metadata.put("weapon", representative.weapon());
        metadata.put("skin_cn", representative.skinCn());
        metadata.put("skin_en", representative.skinEn());
        metadata.put("default_item_id", bucket.defaultItemId());
        metadata.put("variant_item_ids", bucket.itemIds());
        metadata.put("variant_count", bucket.itemIds().size());
        metadata.put("title", representative.titleCn());
        doc.setMetadata(metadata);

        return doc;
    }

    private String buildFamilyContent(FamilyBucket bucket) {
        FamilyItem representative = bucket.representative();
        String weaponAlias = normalizeWeaponAlias(representative.weapon());

        return """
                %s
                %s
                %s
                %s
               
                """
                .formatted(
                        representative.titleCn(),
                        representative.weapon(),
                        weaponAlias,
                        representative.skinCn()

                );
    }

    private FamilyItem toFamilyItem(CsQaqItemIdEntity item) {
        Optional<ParsedItemName> parsedOpt = itemNameParser.parse(
                item.getMarketHashName(),
                item.getCnName()
        );

        if (parsedOpt.isPresent()) {
            ParsedItemName parsed = parsedOpt.get();
            String weapon = stripVariantPrefix(parsed.weapon());
            String familyKey = weapon + "|" + parsed.skinEn();
            return new FamilyItem(
                    familyKey,
                    weapon,
                    parsed.skinCn(),
                    parsed.skinEn(),
                    weapon + " | " + parsed.skinCn(),
                    weapon + " | " + parsed.skinEn(),
                    parsed.exteriorCn(),
                    parsed.exteriorEn(),
                    isStatTrak(item)
            );
        }

        String titleCn = stripVariantPrefix(stripTrailingExterior(item.getCnName()));
        String titleEn = stripVariantPrefix(stripTrailingExterior(item.getMarketHashName()));
        String familyKey = titleEn == null || titleEn.isBlank() ? titleCn : titleEn;
        return new FamilyItem(
                familyKey,
                "",
                titleCn,
                titleEn,
                titleCn,
                titleEn,
                "",
                "",
                isStatTrak(item)
        );
    }

    private boolean isStatTrak(CsQaqItemIdEntity item) {
        return containsStatTrak(item.getCnName()) || containsStatTrak(item.getMarketHashName());
    }

    private boolean containsStatTrak(String value) {
        return value != null && (value.toLowerCase().contains("stattrak") || value.contains("暗金"));
    }

    private String stripVariantPrefix(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("StatTrak™", "")
                .replace("StatTrak", "")
                .replace("暗金", "")
                .replace("（StatTrak™）", "")
                .replace("(StatTrak™)", "")
                .trim();
    }

    private String stripTrailingExterior(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s*[（(][^（）()]+[）)]\\s*$", "").trim();
    }

    private int exteriorPriority(String exteriorCn, String exteriorEn) {
        String text = ((exteriorCn == null ? "" : exteriorCn) + " " + (exteriorEn == null ? "" : exteriorEn)).toLowerCase();
        if (text.contains("崭新") || text.contains("factory new")) {
            return 5;
        }
        if (text.contains("略有") || text.contains("略磨") || text.contains("minimal wear")) {
            return 4;
        }
        if (text.contains("久经") || text.contains("field-tested") || text.contains("field tested")) {
            return 3;
        }
        if (text.contains("破损") || text.contains("well-worn") || text.contains("well worn")) {
            return 2;
        }
        if (text.contains("战痕") || text.contains("battle-scarred") || text.contains("battle scarred")) {
            return 1;
        }
        return 0;
    }

    private record FamilyItem(
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
        private final FamilyItem representative;
        private final List<Long> itemIds = new ArrayList<>();
        private Long defaultItemId;
        private int defaultPriority = Integer.MIN_VALUE;

        private FamilyBucket(FamilyItem representative) {
            this.representative = representative;
        }

        private void add(CsQaqItemIdEntity item, FamilyItem familyItem) {
            itemIds.add(item.getItemId());

            int priority = exteriorPriority(familyItem.exteriorCn(), familyItem.exteriorEn());
            if (!familyItem.statTrak()) {
                priority += 100;
            }

            if (defaultItemId == null || priority > defaultPriority) {
                defaultItemId = item.getItemId();
                defaultPriority = priority;
            }
        }

        private FamilyItem representative() {
            return representative;
        }

        private List<Long> itemIds() {
            return itemIds;
        }

        private Long defaultItemId() {
            return defaultItemId;
        }
    }
}
