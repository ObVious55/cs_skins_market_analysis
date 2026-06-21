package com.example.priceprediction.rag;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 从向量检索的 topK 结果中，基于 metadata/content/词匹配 对候选名称进行优化选择的简单实现。
 * 目的是把用户模糊查询（如"xxx能不能买"）映射到最可能的 item name/item id。
 */
public class TopKQueryOptimizer {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final double DEFAULT_EXTERIOR_BOOST_STEP = 0.01;
    private static final double STAT_TRAK_PENALTY = 0.1;

    /**
     * 精排并返回 topN 候选（按最终 score 降序）和 primary 候选
     */
    public RefinementResult refine(List<VectorStoreClient.VectorSearchResult> results, String userQuery, int topN) {
        if (results == null || results.isEmpty()) {
            return new RefinementResult(null, null, 0.0, List.of());
        }

        String normQuery = normalize(userQuery);
        boolean queryHasExterior = hasExplicitExterior(userQuery);
        boolean queryHasStatTrak = hasExplicitStatTrak(userQuery);
        double maxVectorScore = results.stream().mapToDouble(VectorStoreClient.VectorSearchResult::getScore).max().orElse(1.0);

        List<RefinedCandidate> candidates = new ArrayList<>();

        for (VectorStoreClient.VectorSearchResult r : results) {
            String name = extractName(r);
            double vectorScore = r.getScore();
            double vectorNorm = maxVectorScore <= 0 ? 0 : (vectorScore / maxVectorScore);
            double lexical = lexicalMatchScore(normQuery, name);
            double metaConfidence = (r.getMetadata() != null && !r.getMetadata().isEmpty()) ? 1.0 : 0.0;
            // 简单加权：向量相似度权重0.5，词匹配0.4，metadata置信0.1
            double finalScore = 0.4 * vectorNorm + 0.5 * lexical + 0.1 * metaConfidence;
            if (!queryHasExterior) {
                finalScore += defaultExteriorBoost(r);
            }
            if (!queryHasStatTrak && isStatTrak(r)) {
                finalScore -= STAT_TRAK_PENALTY;
            }

            String itemId = extractItemId(r);
            candidates.add(new RefinedCandidate(itemId, name, finalScore, vectorScore, lexical, r.getMetadata()));
        }

        // 去重（按 itemId 或 name）并按分数排序
        Map<String, RefinedCandidate> dedup = new LinkedHashMap<>();
        for (RefinedCandidate c : candidates) {
            String key = c.getItemId() != null ? c.getItemId() : c.getName();
            RefinedCandidate exist = dedup.get(key);
            if (exist == null || c.getScore() > exist.getScore()) {
                dedup.put(key, c);
            }
        }

        List<RefinedCandidate> sorted = dedup.values().stream()
                .sorted(Comparator.comparingDouble(RefinedCandidate::getScore).reversed())
                .limit(Math.max(1, topN))
                .collect(Collectors.toList());

        RefinedCandidate primary = sorted.isEmpty() ? null : sorted.get(0);
        double confidence = primary == null ? 0.0 : Math.min(1.0, primary.getScore());

        return new RefinementResult(primary == null ? null : primary.getItemId(), primary == null ? null : primary.getName(), confidence, sorted);
    }

    private String normalize(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT).trim();
        // 去除常见问句词
        t = t.replaceAll("\\b(能不能|值不值|可以买吗|值得吗|要不要|怎么样)\\b", " ");
        t = t.replaceAll("[^\\p{L}\\p{N} ]+", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private String extractName(VectorStoreClient.VectorSearchResult r) {
        if (r == null) return "";
        Map<String, Object> m = r.getMetadata();
        if (m != null) {
            Object v = firstNonNull(m.get("name"), m.get("cn_name"), m.get("title"), m.get("item_name"), m.get("market_hash_name"), m.get("marketHashName"));
            if (v != null) return v.toString();
        }
        if (r.getContent() != null && !r.getContent().isBlank()) {
            return snippetToName(r.getContent());
        }
        return "";
    }

    private String extractItemId(VectorStoreClient.VectorSearchResult r) {
        if (r == null) return null;
        Map<String, Object> m = r.getMetadata();
        if (m != null) {
            Object v = firstNonNull(m.get("item_id"), m.get("itemId"), m.get("default_item_id"), m.get("id"), m.get("sku"));
            if (v != null) return v.toString();
        }
        return r.getId();
    }

    private Object firstNonNull(Object... arr) {
        for (Object o : arr) if (o != null) return o;
        return null;
    }

    private String snippetToName(String content) {
        String c = content.trim();
        if (c.length() > 200) c = c.substring(0, 200);
        // 尝试按常见分隔提取第一段或第一行
        String[] lines = c.split("\\r?\\n");
        if (lines.length > 0 && !lines[0].isBlank()) return lines[0].trim();
        return c;
    }

    private double lexicalMatchScore(String normQuery, String name) {
        if (normQuery == null || normQuery.isBlank() || name == null || name.isBlank()) return 0.0;
        String[] qTokens = TOKEN_SPLIT.split(normQuery);
        String[] nameTokens = TOKEN_SPLIT.split(name.toLowerCase(Locale.ROOT));
        if (qTokens.length == 0 || nameTokens.length == 0) return 0.0;
        Set<String> nameSet = Arrays.stream(nameTokens).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        int match = 0;
        for (String t : qTokens) {
            if (t.isBlank()) continue;
            if (nameSet.contains(t)) match++;
            else {
                // 部分匹配：检查子串
                for (String n : nameSet) {
                    if (n.contains(t) || t.contains(n)) { match++; break; }
                }
            }
        }
        return Math.min(1.0, (double) match / Math.max(1, nameSet.size()));
    }

    private boolean hasExplicitExterior(String userQuery) {
        String q = normalize(userQuery);
        if (q.isBlank()) {
            return false;
        }

        return q.contains("崭新出厂")
                || q.contains("略磨")
                || q.contains("略有磨损")
                || q.contains("久经")
                || q.contains("破损")
                || q.contains("战痕")
                || q.contains("factory new")
                || q.contains("minimal wear")
                || q.contains("field tested")
                || q.contains("well worn")
                || q.contains("battle scarred")
                || q.contains("fn")
                || q.contains("mw")
                || q.contains("ft")
                || q.contains("ww")
                || q.contains("bs");
    }

    private double defaultExteriorBoost(VectorStoreClient.VectorSearchResult result) {
        int priority = exteriorPriority(metadataText(result, "exterior_cn", "exterior_en"));
        if (priority <= 0) {
            return 0.0;
        }

        return priority * DEFAULT_EXTERIOR_BOOST_STEP;
    }

    private boolean hasExplicitStatTrak(String userQuery) {
        String q = normalize(userQuery);
        return q.contains("stattrak")
                || q.contains("暗金")
                || q.contains("计数")
                || Arrays.asList(TOKEN_SPLIT.split(q)).contains("st");
    }

    private boolean isStatTrak(VectorStoreClient.VectorSearchResult result) {
        String text = String.join(" ",
                metadataAllText(result, "name", "cn_name", "title", "market_hash_name", "marketHashName"),
                result == null || result.getContent() == null ? "" : result.getContent()
        ).toLowerCase(Locale.ROOT);

        return text.contains("stattrak") || text.contains("暗金");
    }

    private int exteriorPriority(String exterior) {
        String e = normalize(exterior);
        if (e.isBlank()) {
            return 0;
        }

        if (e.contains("崭新") || e.contains("全新") || e.contains("factory new") || e.equals("fn")) {
            return 10;
        }
        if (e.contains("略有") || e.contains("略磨") || e.contains("minimal wear") || e.equals("mw")) {
            return 8;
        }
        if (e.contains("久经") || e.contains("field tested") || e.equals("ft")) {
            return 6;
        }
        if (e.contains("破损") || e.contains("well worn") || e.equals("ww")) {
            return 2;
        }
        if (e.contains("战痕") || e.contains("battle scarred") || e.equals("bs")) {
            return 1;
        }
        return 0;
    }

    private String metadataText(VectorStoreClient.VectorSearchResult result, String... keys) {
        if (result == null || result.getMetadata() == null) {
            return "";
        }
        for (String key : keys) {
            Object value = result.getMetadata().get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return "";
    }

    private String metadataAllText(VectorStoreClient.VectorSearchResult result, String... keys) {
        if (result == null || result.getMetadata() == null) {
            return "";
        }

        List<String> values = new ArrayList<>();
        for (String key : keys) {
            Object value = result.getMetadata().get(key);
            if (value != null) {
                values.add(value.toString());
            }
        }
        return String.join(" ", values);
    }
}
