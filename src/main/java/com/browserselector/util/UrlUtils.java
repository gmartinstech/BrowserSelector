package com.browserselector.util;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class UrlUtils {

    private UrlUtils() {}

    public static String extractDomain(String url) {
        if (url == null || url.isBlank()) return "";

        var decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);

        try {
            var uri = URI.create(decoded);
            var host = uri.getHost();
            return host != null ? host.toLowerCase() : "";
        } catch (Exception e) {
            // Fallback: try to extract domain manually
            var cleaned = decoded.replaceFirst("^https?://", "");
            var slashIdx = cleaned.indexOf('/');
            var domain = slashIdx > 0 ? cleaned.substring(0, slashIdx) : cleaned;
            var colonIdx = domain.indexOf(':');
            return (colonIdx > 0 ? domain.substring(0, colonIdx) : domain).toLowerCase();
        }
    }

    public static String extractPath(String url) {
        if (url == null || url.isBlank()) return "";

        try {
            var uri = URI.create(URLDecoder.decode(url, StandardCharsets.UTF_8));
            var path = uri.getPath();
            return path != null ? path : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String normalizeUrl(String url) {
        if (url == null) return "";

        var trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) return false;

        try {
            var uri = URI.create(url);
            var scheme = uri.getScheme();
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (Exception e) {
            return false;
        }
    }
}
