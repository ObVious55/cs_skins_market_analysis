package com.example.priceprediction.service;

import org.springframework.stereotype.Service;

@Service
public class ClarificationService {

    private static final int CONFIRMATION_CANDIDATE_LIMIT = 3;

    public AgentResponsePlan lowConfidenceFamily(ItemFamilyRecallResult familyRecall) {
        StringBuilder reply = new StringBuilder();
        reply.append("我不太确定你指的是哪一个饰品大类，先不直接查询价格或 K 线，避免拿错数据。\n\n");
        reply.append("目前最接近的候选是：\n");

        int count = 0;
        if (familyRecall.getCandidates() != null) {
            for (ItemFamilyRecallCandidate candidate : familyRecall.getCandidates()) {
                if (candidate == null || candidate.getName() == null || candidate.getName().isBlank()) {
                    continue;
                }
                count++;
                reply.append(count)
                        .append(". ")
                        .append(candidate.getName())
                        .append("（置信度 ")
                        .append(String.format("%.2f", Math.min(1.0, candidate.getFinalScore())))
                        .append("，来源 ")
                        .append(String.join("+", candidate.getSources()))
                        .append("）\n");
                if (count >= CONFIRMATION_CANDIDATE_LIMIT) {
                    break;
                }
            }
        }

        if (count == 0) {
            reply.append("- 暂时没有足够可靠的候选结果\n");
        }

        reply.append("\n请你确认要分析的是哪一个饰品，最好补充完整名称或磨损，例如“AK-47 | 红线（略有磨损）”。");
        return AgentResponsePlan.askConfirmation(reply.toString());
    }

}
