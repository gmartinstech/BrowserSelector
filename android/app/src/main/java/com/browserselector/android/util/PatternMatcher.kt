package com.browserselector.android.util

import java.net.URI
import java.util.regex.Pattern

/**
 * Utility for matching URLs against wildcard patterns.
 * Supports wildcards:
 * - * matches anything except /
 * - ** matches anything including /
 * - ? matches any single character
 */
object PatternMatcher {

    /**
     * Checks if a URL matches the given pattern.
     */
    fun matches(pattern: String, url: String): Boolean {
        if (pattern.isBlank() || url.isBlank()) return false

        val normalizedUrl = normalizeUrl(url)
        val regex = patternToRegex(pattern)

        return try {
            val compiledPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
            compiledPattern.matcher(normalizedUrl).matches()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Converts a wildcard pattern to a regex pattern.
     */
    fun patternToRegex(pattern: String): String {
        val sb = StringBuilder()
        sb.append("^")

        // If pattern doesn't start with scheme, make it optional
        if (!pattern.startsWith("http://") && !pattern.startsWith("https://")) {
            sb.append("(?:https?://)?(?:www\\.)?")
        }

        var i = 0
        while (i < pattern.length) {
            when {
                // Handle ** (matches anything including /)
                i < pattern.length - 1 && pattern[i] == '*' && pattern[i + 1] == '*' -> {
                    sb.append(".*")
                    i += 2
                }
                // Handle * (matches anything except /)
                pattern[i] == '*' -> {
                    sb.append("[^/]*")
                    i++
                }
                // Handle ? (matches any single character)
                pattern[i] == '?' -> {
                    sb.append(".")
                    i++
                }
                // Escape regex special characters
                pattern[i] in "\\[]{}()+^$|" -> {
                    sb.append("\\")
                    sb.append(pattern[i])
                    i++
                }
                // Handle . (escape it)
                pattern[i] == '.' -> {
                    sb.append("\\.")
                    i++
                }
                // Regular character
                else -> {
                    sb.append(pattern[i])
                    i++
                }
            }
        }

        // Allow trailing path/query if not specified
        if (!pattern.contains("/") || pattern.endsWith("*")) {
            sb.append("(?:/.*)?")
        }
        sb.append("$")

        return sb.toString()
    }

    /**
     * Validates a pattern.
     */
    fun isValidPattern(pattern: String): Boolean {
        if (pattern.isBlank()) return false

        return try {
            val regex = patternToRegex(pattern)
            Pattern.compile(regex)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts the domain from a URL.
     */
    fun extractDomain(url: String): String? {
        return try {
            val normalized = normalizeUrl(url)
            val uri = URI(normalized)
            uri.host?.removePrefix("www.")
        } catch (e: Exception) {
            // Fallback: try regex extraction
            val regex = Regex("(?:https?://)?(?:www\\.)?([^/]+)")
            regex.find(url)?.groupValues?.getOrNull(1)
        }
    }

    /**
     * Converts a domain to a wildcard pattern.
     */
    fun domainToPattern(domain: String): String {
        val cleanDomain = domain.removePrefix("www.").trim()
        return "*.$cleanDomain"
    }

    /**
     * Normalizes a URL by adding https:// if no scheme is present.
     */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    /**
     * Checks if a URL is valid.
     */
    fun isValidUrl(url: String): Boolean {
        return try {
            val normalized = normalizeUrl(url)
            val uri = URI(normalized)
            uri.scheme != null && uri.host != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Truncates a URL for display.
     */
    fun truncateUrl(url: String, maxLength: Int = 60): String {
        if (url.length <= maxLength) return url
        return url.take(maxLength - 3) + "..."
    }
}
