package com.example.priceprediction.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ItemNameParser {

    private static final Pattern SKIN_PATTERN =
            Pattern.compile("^(.+?) \\| (.+?) \\((.+?)\\)$");

    public Optional<ParsedItemName> parse(String enName, String cnName) {
        if (enName == null || cnName == null) {
            return Optional.empty();
        }

        Matcher enMatcher = SKIN_PATTERN.matcher(enName);
        Matcher cnMatcher = SKIN_PATTERN.matcher(cnName);

        if (!enMatcher.matches() || !cnMatcher.matches()) {
            return Optional.empty();
        }

        String weapon = enMatcher.group(1).trim();

        String skinEn = enMatcher.group(2).trim();
        String exteriorEn = enMatcher.group(3).trim();

        String skinCn = cnMatcher.group(2).trim();
        String exteriorCn = cnMatcher.group(3).trim();

        String familyKey = weapon + "|" + skinEn;

        return Optional.of(new ParsedItemName(
                weapon,
                skinEn,
                skinCn,
                exteriorEn,
                exteriorCn,
                familyKey
        ));
    }

    public record ParsedItemName(
            String weapon,
            String skinEn,
            String skinCn,
            String exteriorEn,
            String exteriorCn,
            String familyKey
    ) {
    }
}