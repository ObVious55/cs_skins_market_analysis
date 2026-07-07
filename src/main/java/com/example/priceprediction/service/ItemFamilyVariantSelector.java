package com.example.priceprediction.service;

import com.example.priceprediction.entity.CsQaqItemIdEntity;
import com.example.priceprediction.repository.CsQaqItemIdRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ItemFamilyVariantSelector {

    private final CsQaqItemIdRepository csQaqItemIdRepository;

    public ItemFamilyVariantSelector(CsQaqItemIdRepository csQaqItemIdRepository) {
        this.csQaqItemIdRepository = csQaqItemIdRepository;
    }

    public Optional<CsQaqItemIdEntity> selectVariant(ItemFamilyRecallCandidate family, String query) {
        if (family == null) {
            return Optional.empty();
        }

        List<CsQaqItemIdEntity> variants = loadVariants(family);
        if (variants.isEmpty()) {
            return family.getDefaultItemId() == null
                    ? Optional.empty()
                    : csQaqItemIdRepository.findByItemId(family.getDefaultItemId());
        }

        VariantPreference preference = VariantPreference.from(query);
        return variants.stream()
                .max(Comparator.comparingInt(item -> score(item, preference)))
                .or(() -> family.getDefaultItemId() == null
                        ? Optional.empty()
                        : csQaqItemIdRepository.findByItemId(family.getDefaultItemId()));
    }

    private List<CsQaqItemIdEntity> loadVariants(ItemFamilyRecallCandidate family) {
        if (family.getVariantItemIds() != null && !family.getVariantItemIds().isEmpty()) {
            return csQaqItemIdRepository.findByItemIdIn(family.getVariantItemIds());
        }
        if (family.getDefaultItemId() != null) {
            return csQaqItemIdRepository.findByItemId(family.getDefaultItemId())
                    .map(List::of)
                    .orElse(List.of());
        }
        return List.of();
    }

    private int score(CsQaqItemIdEntity item, VariantPreference preference) {
        int score = exteriorScore(item, preference);
        boolean statTrak = isStatTrak(item);

        if (preference.statTrakRequested()) {
            score += statTrak ? 1000 : -1000;
        } else {
            score += statTrak ? -100 : 100;
        }

        return score;
    }

    private int exteriorScore(CsQaqItemIdEntity item, VariantPreference preference) {
        int itemRank = exteriorRank(item);
        if (preference.exteriorRank() > 0) {
            return itemRank == preference.exteriorRank() ? 500 : -Math.abs(preference.exteriorRank() - itemRank);
        }
        return itemRank * 10;
    }

    private int exteriorRank(CsQaqItemIdEntity item) {
        String text = normalize(item.getCnName() + " " + item.getMarketHashName());
        if (text.contains("崭新") || text.contains("全新") || text.contains("factory new")) {
            return 5;
        }
        if (text.contains("略磨") || text.contains("略有磨损") || text.contains("minimal wear")) {
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

    private boolean isStatTrak(CsQaqItemIdEntity item) {
        String text = normalize(item.getCnName() + " " + item.getMarketHashName());
        return text.contains("stattrak") || text.contains("暗金") || text.contains("计数");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record VariantPreference(int exteriorRank, boolean statTrakRequested) {

        private static VariantPreference from(String query) {
            String text = query == null ? "" : query.toLowerCase(Locale.ROOT);
            return new VariantPreference(detectExteriorRank(text), detectStatTrak(text));
        }

        private static int detectExteriorRank(String text) {
            if (text.contains("崭新") || text.contains("全新") || text.contains("factory new")
                    || text.matches(".*\\bfn\\b.*")) {
                return 5;
            }
            if (text.contains("略磨") || text.contains("略有") || text.contains("minimal wear")
                    || text.matches(".*\\bmw\\b.*")) {
                return 4;
            }
            if (text.contains("久经") || text.contains("field-tested") || text.contains("field tested")
                    || text.matches(".*\\bft\\b.*")) {
                return 3;
            }
            if (text.contains("破损") || text.contains("well-worn") || text.contains("well worn")
                    || text.matches(".*\\bww\\b.*")) {
                return 2;
            }
            if (text.contains("战痕") || text.contains("battle-scarred") || text.contains("battle scarred")
                    || text.matches(".*\\bbs\\b.*")) {
                return 1;
            }
            return 0;
        }

        private static boolean detectStatTrak(String text) {
            return text.contains("stattrak")
                    || text.contains("stat trak")
                    || text.contains("暗金")
                    || text.contains("计数")
                    || text.matches(".*\\bst\\b.*");
        }
    }
}
