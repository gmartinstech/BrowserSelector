import Foundation

/// Utility for matching URLs against wildcard patterns.
/// Supports wildcards:
/// - * matches anything except /
/// - ** matches anything including /
/// - ? matches any single character
enum PatternMatcher {

    /// Checks if a URL matches the given pattern.
    static func matches(pattern: String, url: String) -> Bool {
        guard !pattern.isEmpty, !url.isEmpty else { return false }

        let normalizedUrl = normalizeUrl(url)
        let regex = patternToRegex(pattern)
        let patternLower = pattern.lowercased()
        let domain = extractDomain(from: url)?.lowercased() ?? ""

        do {
            let compiledPattern = try NSRegularExpression(pattern: regex, options: .caseInsensitive)
            let range = NSRange(normalizedUrl.startIndex..., in: normalizedUrl)

            if compiledPattern.firstMatch(in: normalizedUrl, range: range) != nil {
                return true
            }

            // Special handling for plain domain patterns (no wildcards):
            // A pattern like "google.com" should match both "google.com" and "www.google.com"
            if !pattern.contains("*") && !pattern.contains("?") && !pattern.contains("/") {
                if domain.hasSuffix(".\(patternLower)") || domain == patternLower {
                    return true
                }
            }

            // Special handling for *.domain patterns: also match the bare domain
            // Pattern "*.google.com" should also match "google.com"
            if patternLower.hasPrefix("*.") {
                let baseDomain = String(patternLower.dropFirst(2))
                if domain == baseDomain {
                    return true
                }
            }

            return false
        } catch {
            return false
        }
    }

    /// Converts a wildcard pattern to a regex pattern.
    static func patternToRegex(_ pattern: String) -> String {
        var result = "^"

        // If pattern doesn't start with scheme, make it optional
        if !pattern.hasPrefix("http://") && !pattern.hasPrefix("https://") {
            result += "(?:https?://)?(?:www\\.)?"
        }

        var i = pattern.startIndex
        while i < pattern.endIndex {
            let c = pattern[i]

            // Handle ** (matches anything including /)
            if c == "*" && pattern.index(after: i) < pattern.endIndex && pattern[pattern.index(after: i)] == "*" {
                result += ".*"
                i = pattern.index(i, offsetBy: 2)
                continue
            }

            switch c {
            case "*":
                result += "[^/]*"
            case "?":
                result += "."
            case ".", "+", "^", "$", "|", "[", "]", "(", ")", "{", "}", "\\":
                result += "\\\(c)"
            default:
                result += String(c)
            }

            i = pattern.index(after: i)
        }

        // Allow trailing path/query if not specified
        if !pattern.contains("/") || pattern.hasSuffix("*") {
            result += "(?:/.*)?"
        }
        result += "$"

        return result
    }

    /// Validates a pattern.
    static func isValidPattern(_ pattern: String) -> Bool {
        guard !pattern.trimmingCharacters(in: .whitespaces).isEmpty else { return false }

        do {
            let regex = patternToRegex(pattern)
            _ = try NSRegularExpression(pattern: regex)
            return true
        } catch {
            return false
        }
    }

    /// Extracts the domain from a URL.
    static func extractDomain(from url: String) -> String? {
        let normalized = normalizeUrl(url)
        guard let urlObj = URL(string: normalized) else {
            // Fallback: regex extraction
            let regex = try? NSRegularExpression(pattern: "(?:https?://)?(?:www\\.)?([^/]+)")
            let range = NSRange(url.startIndex..., in: url)
            if let match = regex?.firstMatch(in: url, range: range),
               let domainRange = Range(match.range(at: 1), in: url) {
                return String(url[domainRange])
            }
            return nil
        }
        return urlObj.host?.replacingOccurrences(of: "^www\\.", with: "", options: .regularExpression)
    }

    /// Converts a domain to a pattern for rule creation.
    static func domainToPattern(_ domain: String) -> String {
        domain.trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: "^www\\.", with: "", options: .regularExpression)
    }

    /// Normalizes a URL by adding https:// if no scheme is present.
    static func normalizeUrl(_ url: String) -> String {
        let trimmed = url.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            return trimmed
        }
        return "https://\(trimmed)"
    }

    /// Checks if a URL is valid.
    static func isValidUrl(_ url: String) -> Bool {
        let normalized = normalizeUrl(url)
        guard let urlObj = URL(string: normalized) else { return false }
        return urlObj.scheme != nil && urlObj.host != nil
    }

    /// Truncates a URL for display.
    static func truncateUrl(_ url: String, maxLength: Int = 60) -> String {
        if url.count <= maxLength { return url }
        return String(url.prefix(maxLength - 3)) + "..."
    }
}
