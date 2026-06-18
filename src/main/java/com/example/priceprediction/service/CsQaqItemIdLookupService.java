package com.example.priceprediction.service;

import com.example.priceprediction.entity.CsQaqItemIdEntity;
import com.example.priceprediction.repository.CsQaqItemIdRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CsQaqItemIdLookupService {

    private final CsQaqItemIdRepository csQaqItemIdRepository;

    public CsQaqItemIdLookupService(CsQaqItemIdRepository csQaqItemIdRepository) {
        this.csQaqItemIdRepository = csQaqItemIdRepository;
    }

    public Optional<CsQaqItemIdEntity> findByRagPrimaryName(String primaryName) {
        if (!StringUtils.hasText(primaryName)) {
            return Optional.empty();
        }

        String name = primaryName.trim();

        // 生成所有候选名：原名、别名、补磨损后的原名、补磨损后的别名
        List<String> candidates = buildSearchCandidates(name);

        for (String candidate : candidates) {
            Optional<CsQaqItemIdEntity> itemOpt =
                    csQaqItemIdRepository.findFirstByExactName(candidate);

            if (itemOpt.isPresent()) {
                return itemOpt;
            }
        }

        return Optional.empty();
    }

    public Optional<CsQaqItemIdEntity> findByItemId(String itemId) {
        if (!StringUtils.hasText(itemId) || !itemId.trim().matches("\\d+")) {
            return Optional.empty();
        }
        return csQaqItemIdRepository.findByItemId(Long.valueOf(itemId.trim()));
    }

    private List<String> buildSearchCandidates(String name) {
        Set<String> candidates = new LinkedHashSet<>();

        if (!StringUtils.hasText(name)) {
            return new ArrayList<>(candidates);
        }

        String originalName = name.trim();

        // 1. 原始 RAG 名称
        candidates.add(originalName);

        // 2. 武器别名归一化后的名称
        String aliasName = normalizeWeaponAlias(originalName);
        candidates.add(aliasName);

        // 3. 如果已经包含磨损值：只查原名和别名名，不再补新的磨损
        if (hasExterior(originalName)) {
            return new ArrayList<>(candidates);
        }

        // 4. 如果没有磨损值：对原名补磨损
        addDefaultExteriorCandidates(candidates, originalName);

        // 5. 如果没有磨损值：对别名名也补磨损
        addDefaultExteriorCandidates(candidates, aliasName);

        return new ArrayList<>(candidates);
    }

    private void addDefaultExteriorCandidates(Set<String> candidates, String name) {
        if (!StringUtils.hasText(name)) {
            return;
        }

        // 投资场景默认优先崭新出厂
        candidates.add(name + " (崭新出厂)");
        candidates.add(name + " (Factory New)");

        // 兜底其他磨损
        candidates.add(name + " (略有磨损)");
        candidates.add(name + " (Minimal Wear)");

        candidates.add(name + " (久经沙场)");
        candidates.add(name + " (Field-Tested)");

        candidates.add(name + " (破损不堪)");
        candidates.add(name + " (Well-Worn)");

        candidates.add(name + " (战痕累累)");
        candidates.add(name + " (Battle-Scarred)");
    }

    private String normalizeWeaponAlias(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }

        return name
                .replace("USP-S", "USP 消音版")
                .replace("USP消音版", "USP 消音版")
                .replace("Glock-18","格洛克 18 型")
                .replace("Galil AR","加利尔 AR")
                .replace("★ Moto Gloves","摩托手套（★）")
                .replace("★ Specialist Gloves","专业手套（★）");
    }

    private boolean hasExterior(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }

        String normalized = name.trim().toLowerCase();

        return normalized.contains("崭新出厂")
                || normalized.contains("略有磨损")
                || normalized.contains("久经沙场")
                || normalized.contains("破损不堪")
                || normalized.contains("战痕累累")
                || normalized.contains("factory new")
                || normalized.contains("minimal wear")
                || normalized.contains("field-tested")
                || normalized.contains("field tested")
                || normalized.contains("well-worn")
                || normalized.contains("well worn")
                || normalized.contains("battle-scarred")
                || normalized.contains("battle scarred");
    }
}
