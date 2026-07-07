package com.example.priceprediction.service;

import java.math.BigDecimal;
import java.util.Set;

public record IntentClassification(
        IntentType primaryIntent,
        Set<IntentFlag> flags,
        String itemText,
        String action,
        BigDecimal priceLimit,
        String wear,
        boolean followUp,
        double confidence,
        String reason
) {
    public static IntentClassification unknown(String reason) {
        return new IntentClassification(
                IntentType.UNKNOWN,
                Set.of(),
                null,
                null,
                null,
                null,
                false,
                0.0,
                reason
        );
    }

    public IntentType intentType() {
        return primaryIntent;
    }

    public boolean hasFlag(IntentFlag flag) {
        return flags != null && flags.contains(flag);
    }
}
