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

    public List<ItemRagDocument> buildAllDocuments() {
        List<CsQaqItemIdEntity> items = csQaqItemIdRepository.findAll();
        List<ItemRagDocument> documents = new ArrayList<>();

        for (CsQaqItemIdEntity item : items) {
            documents.add(buildQaqItemDocument(item));
        }

        return documents;
    }

    private ItemRagDocument buildQaqItemDocument(CsQaqItemIdEntity item) {
        ItemRagDocument doc = new ItemRagDocument();
        Optional<ParsedItemName> parsedOpt = itemNameParser.parse(
                item.getMarketHashName(),
                item.getCnName()
        );

        ParsedItemName parsed = parsedOpt.orElse(null);
        String weapon = parsed == null ? "" : parsed.weapon();
        String skinCn = parsed == null ? "" : parsed.skinCn();
        String skinEn = parsed == null ? "" : parsed.skinEn();
        String exteriorCn = parsed == null ? "" : parsed.exteriorCn();
        String exteriorEn = parsed == null ? "" : parsed.exteriorEn();

        doc.setDocId("QAQ_ITEM:" + item.getItemId());
        doc.setDocType("QAQ_ITEM");
        doc.setNameId(item.getItemId());
        doc.setFamilyKey(parsed == null ? null : parsed.familyKey());
        doc.setWeapon(weapon);
        doc.setSkinCn(skinCn);
        doc.setSkinEn(skinEn);
        doc.setExteriorCn(exteriorCn);
        doc.setExteriorEn(exteriorEn);
        doc.setTitle(item.getCnName());
        doc.setContent(buildQaqItemContent(item, weapon, skinCn, skinEn, exteriorCn, exteriorEn));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("doc_type", "QAQ_ITEM");
        metadata.put("item_id", item.getItemId());
        metadata.put("name", item.getCnName());
        metadata.put("cn_name", item.getCnName());
        metadata.put("market_hash_name", item.getMarketHashName());
        metadata.put("weapon", weapon);
        metadata.put("skin_cn", skinCn);
        metadata.put("skin_en", skinEn);
        metadata.put("exterior_cn", exteriorCn);
        metadata.put("exterior_en", exteriorEn);
        metadata.put("title", item.getCnName());
        doc.setMetadata(metadata);

        return doc;
    }

    private String buildQaqItemContent(
            CsQaqItemIdEntity item,
            String weapon,
            String skinCn,
            String skinEn,
            String exteriorCn,
            String exteriorEn
    ) {
        String weaponAlias = normalizeWeaponAlias(weapon);
        String exteriorAlias = normalizeExteriorAlias(exteriorCn);

        return """
                CS2 饰品标准条目。
                itemId：%d
                中文名：%s
                英文 market_hash_name：%s
                武器：%s
                武器别名：%s
                皮肤中文名：%s
                皮肤英文名：%s
                磨损中文名：%s
                磨损英文名：%s
                磨损别名：%s
                用户可能会说：%s、%s、%s、%s、%s、%s
                该文档来自 cs_qaq_item_id 表，metadata.item_id 就是价格、K 线和策略工具应使用的最终业务 itemId。
                """
                .formatted(
                        item.getItemId(),
                        item.getCnName(),
                        item.getMarketHashName(),
                        weapon,
                        weaponAlias,
                        skinCn,
                        skinEn,
                        exteriorCn,
                        exteriorEn,
                        exteriorAlias,
                        item.getCnName(),
                        item.getMarketHashName(),
                        weapon + skinCn + exteriorCn,
                        weaponAlias + skinCn + exteriorAlias,
                        skinCn + exteriorAlias,
                        skinEn + " " + exteriorEn
                );
    }

    private String normalizeWeaponAlias(String weapon) {
        if (weapon == null) {
            return "";
        }

        return switch (weapon) {
            case "AK-47" -> "AK、AK47、ak";
            case "Desert Eagle" -> "沙鹰、Deagle";
            case "AWP" -> "大狙、龙狙、awp";
            case "Glock-18" -> "格洛克、Glock、Glock18、格洛克 18 型";
            case "SSG 08" -> "鸟狙、SSG08";
            case "M4A1-S" -> "M4A1、M4A1S、M4A1 消音版";
            case "USP-S" -> "USP、USP 消音版";
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
}
