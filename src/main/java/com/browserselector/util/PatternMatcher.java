package com.browserselector.util;

import java.util.regex.Pattern;

public final class PatternMatcher {

    private PatternMatcher() {}

    public static boolean matches(String pattern, String url) {
        if (pattern == null || url == null) return false;

        var domain = UrlUtils.extractDomain(url);
        var path = UrlUtils.extractPath(url);
        var fullMatch = domain + path;

        // Convert wildcard pattern to regex
        var regex = patternToRegex(pattern.toLowerCase());

        return Pattern.matches(regex, fullMatch.toLowerCase()) ||
               Pattern.matches(regex, domain.toLowerCase());
    }

    private static String patternToRegex(String pattern) {
        var sb = new StringBuilder();
        sb.append("^");

        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*' -> {
                    // Check for ** (match across path separators)
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                        sb.append(".*");
                        i++; // Skip next *
                    } else {
                        // Single * matches anything except /
                        sb.append("[^/]*");
                    }
                }
                case '?' -> sb.append(".");
                case '.' -> sb.append("\\.");
                case '\\' -> sb.append("\\\\");
                case '^', '$', '|', '+', '[', ']', '(', ')', '{', '}' -> {
                    sb.append("\\");
                    sb.append(c);
                }
                default -> sb.append(c);
            }
        }

        sb.append("$");
        return sb.toString();
    }

    public static boolean isValidPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) return false;

        // Must contain at least one non-wildcard character
        var stripped = pattern.replace("*", "").replace("?", "");
        if (stripped.isBlank()) return false;

        try {
            patternToRegex(pattern);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String domainToPattern(String domain) {
        if (domain == null || domain.isBlank()) return "";

        // If already a pattern, return as-is
        if (domain.contains("*") || domain.contains("?")) {
            return domain;
        }

        // Create a pattern that matches the domain and all subdomains
        return "*." + domain;
    }
}
