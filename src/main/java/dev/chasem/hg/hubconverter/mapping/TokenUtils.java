package dev.chasem.hg.hubconverter.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TokenUtils {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[_\\-\\s]+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Z]?[a-z]+|[A-Z]+(?![a-z])|\\d+");

    private TokenUtils() {
    }

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = SPLIT_PATTERN.split(text);
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            Matcher matcher = TOKEN_PATTERN.matcher(part);
            while (matcher.find()) {
                String token = matcher.group();
                if (!token.isBlank()) {
                    tokens.add(token.toLowerCase(Locale.ROOT));
                }
            }
        }
        return tokens;
    }

    public static List<String> normalizeTokens(List<String> tokens, Map<String, List<String>> synonyms) {
        if (tokens.isEmpty()) {
            return tokens;
        }
        List<String> expanded = new ArrayList<>();
        for (String token : tokens) {
            List<String> replacement = synonyms.get(token);
            if (replacement != null) {
                expanded.addAll(replacement);
            } else {
                expanded.add(token);
            }
        }
        return expanded;
    }
}
