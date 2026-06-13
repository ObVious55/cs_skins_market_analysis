package com.example.priceprediction.rag;

import com.example.priceprediction.entity.CsItemRawEntity;
import com.example.priceprediction.repository.CsItemRawRepository;
import com.example.priceprediction.service.ItemNameParser;
import com.example.priceprediction.service.ItemNameParser.ParsedItemName;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ItemRagDocumentBuilder {

    private final CsItemRawRepository rawRepository;
    private final ItemNameParser itemNameParser;

    public ItemRagDocumentBuilder(CsItemRawRepository rawRepository,
                                  ItemNameParser itemNameParser) {
        this.rawRepository = rawRepository;
        this.itemNameParser = itemNameParser;
    }

    public List<ItemRagDocument> buildAllDocuments() {
        List<CsItemRawEntity> rawItems = rawRepository.findAll();

        List<ParsedRawItem> parsedItems = new ArrayList<>();
        List<ItemRagDocument> documents = new ArrayList<>();

        for (CsItemRawEntity raw : rawItems) {
            Optional<ParsedItemName> optional = itemNameParser.parse(
                    raw.getEnName(),
                    raw.getCnName()
            );

            if (optional.isEmpty()) {
                continue;
            }

            ParsedItemName parsed = optional.get();
            parsedItems.add(new ParsedRawItem(raw, parsed));

            documents.add(buildVariantDocument(raw, parsed));
        }

        documents.addAll(buildFamilyDocuments(parsedItems));

        return documents;
    }

    private ItemRagDocument buildVariantDocument(CsItemRawEntity raw, ParsedItemName p) {
        ItemRagDocument doc = new ItemRagDocument();

        doc.setDocId("ITEM_VARIANT:" + raw.getNameId());
        doc.setDocType("ITEM_VARIANT");

        doc.setNameId(raw.getNameId());
        doc.setFamilyKey(p.familyKey());

        doc.setWeapon(p.weapon());
        doc.setSkinCn(p.skinCn());
        doc.setSkinEn(p.skinEn());
        doc.setExteriorCn(p.exteriorCn());
        doc.setExteriorEn(p.exteriorEn());

        doc.setTitle(raw.getCnName());
        doc.setContent(buildVariantContent(raw, p));

        doc.setMetadata(Map.of(
                "doc_type", "ITEM_VARIANT",
                "name_id", raw.getNameId(),
                "family_key", p.familyKey(),
                "weapon", p.weapon(),
                "skin_cn", p.skinCn(),
                "skin_en", p.skinEn(),
                "exterior_cn", p.exteriorCn(),
                "exterior_en", p.exteriorEn(),
                "title", raw.getCnName()
        ));

        return doc;
    }

    private List<ItemRagDocument> buildFamilyDocuments(List<ParsedRawItem> parsedItems) {
        Map<String, List<ParsedRawItem>> familyMap = parsedItems.stream()
                .collect(Collectors.groupingBy(item -> item.parsed().familyKey()));

        List<ItemRagDocument> documents = new ArrayList<>();

        for (Map.Entry<String, List<ParsedRawItem>> entry : familyMap.entrySet()) {
            String familyKey = entry.getKey();
            List<ParsedRawItem> familyItems = entry.getValue();

            if (familyItems.isEmpty()) {
                continue;
            }

            ParsedItemName p = familyItems.get(0).parsed();

            List<String> exteriors = familyItems.stream()
                    .map(item -> item.parsed().exteriorCn())
                    .distinct()
                    .toList();

            List<Long> nameIds = familyItems.stream()
                    .map(item -> item.raw().getNameId())
                    .toList();

            ItemRagDocument doc = new ItemRagDocument();

            doc.setDocId("ITEM_FAMILY:" + familyKey);
            doc.setDocType("ITEM_FAMILY");

            doc.setFamilyKey(familyKey);
            doc.setNameIds(nameIds);

            doc.setWeapon(p.weapon());
            doc.setSkinCn(p.skinCn());
            doc.setSkinEn(p.skinEn());

            doc.setTitle(p.weapon() + " | " + p.skinCn());
            doc.setContent(buildFamilyContent(p, exteriors));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("doc_type", "ITEM_FAMILY");
            metadata.put("family_key", familyKey);
            metadata.put("name_ids", nameIds);
            metadata.put("weapon", p.weapon());
            metadata.put("skin_cn", p.skinCn());
            metadata.put("skin_en", p.skinEn());
            metadata.put("title", p.weapon() + " | " + p.skinCn());

            doc.setMetadata(metadata);

            documents.add(doc);
        }

        return documents;
    }

    private String buildVariantContent(CsItemRawEntity raw, ParsedItemName p) {
        String weaponAlias = normalizeWeaponAlias(p.weapon());
        String exteriorAlias = normalizeExteriorAlias(p.exteriorCn());

        return """
                中文名：%s。
                英文名：%s。
                武器：%s，也可能被用户称为：%s。
                皮肤：%s，英文名：%s。
                磨损：%s，英文名：%s，也可能被用户称为：%s。
                用户可能会说：%s、%s、%s、%s、%s。
                这是具体磨损版本。如果用户同时提到武器、皮肤和磨损，应匹配该版本。
                """
                .formatted(
                        raw.getCnName(),
                        raw.getEnName(),
                        p.weapon(),
                        weaponAlias,
                        p.skinCn(),
                        p.skinEn(),
                        p.exteriorCn(),
                        p.exteriorEn(),
                        exteriorAlias,
                        p.weapon() + p.skinCn() + p.exteriorCn(),
                        weaponAlias + p.skinCn() + exteriorAlias,
                        p.skinCn() + exteriorAlias,
                        p.weapon() + p.skinCn(),
                        p.skinEn() + " " + p.exteriorEn()
                );
    }

    private String buildFamilyContent(ParsedItemName p, List<String> exteriors) {
        String weaponAlias = normalizeWeaponAlias(p.weapon());

        return """
                中文名：%s | %s。
                英文名：%s | %s。
                这是一个 CS2 饰品组，不限定具体磨损。
                武器：%s，也可能被用户称为：%s。
                皮肤：%s，英文名：%s。
                包含磨损版本：%s。
                用户可能会说：%s、%s、%s、%s、%s。
                如果用户只提到武器和皮肤，但没有指定磨损，应匹配这个饰品组。
                """
                .formatted(
                        p.weapon(),
                        p.skinCn(),
                        p.weapon(),
                        p.skinEn(),
                        p.weapon(),
                        weaponAlias,
                        p.skinCn(),
                        p.skinEn(),
                        String.join("、", exteriors),
                        p.weapon() + p.skinCn(),
                        weaponAlias + p.skinCn(),
                        p.skinCn(),
                        p.weapon() + " " + p.skinEn(),
                        p.skinEn()
                );
    }

    private String normalizeWeaponAlias(String weapon) {
        if (weapon == null) {
            return "";
        }

        return switch (weapon) {
            case "AK-47" -> "AK、AK47、ak";
            case "Desert Eagle" -> "沙鹰、Deagle";
            case "AWP" -> "大狙、awp";
            case "Glock-18" -> "格洛克、Glock";
            case "SSG 08" -> "鸟狙、SSG08";
            case "M4A1-S" -> "M4A1、M4A1S";
            default -> weapon;
        };
    }

    private String normalizeExteriorAlias(String exteriorCn) {
        if (exteriorCn == null) {
            return "";
        }

        return switch (exteriorCn) {
            case "崭新出厂" -> "崭新、全新、FN";
            case "略有磨损" -> "略磨、略有、MW";
            case "久经沙场" -> "久经、FT";
            case "破损不堪" -> "破损、WW";
            case "战痕累累" -> "战痕、BS";
            default -> exteriorCn;
        };
    }

    private record ParsedRawItem(
            CsItemRawEntity raw,
            ParsedItemName parsed
    ) {
    }
}