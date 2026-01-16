package com.browserselector.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PatternMatcherTest {

    @Nested
    @DisplayName("matches() domain patterns")
    class MatchesDomainPatterns {

        @ParameterizedTest
        @CsvSource({
            "google.com, https://google.com, true",
            "google.com, http://google.com, true",
            "google.com, https://google.com/search, true",
            "example.org, https://example.org/page, true"
        })
        @DisplayName("exact domain pattern matches domain")
        void exactDomainMatches(String pattern, String url, boolean expected) {
            assertThat(PatternMatcher.matches(pattern, url)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
            "google.com, https://www.google.com, true",
            "google.com, https://mail.google.com, true",
            "google.com, https://docs.google.com/document, true",
            "example.org, https://blog.example.org, true"
        })
        @DisplayName("domain pattern matches subdomains")
        void domainMatchesSubdomains(String pattern, String url, boolean expected) {
            assertThat(PatternMatcher.matches(pattern, url)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
            "*.google.com, https://mail.google.com, true",
            "*.google.com, https://docs.google.com, true",
            "*.google.com, https://www.google.com, true",
            "*.example.org, https://api.example.org/v1, true"
        })
        @DisplayName("wildcard pattern matches subdomains")
        void wildcardMatchesSubdomains(String pattern, String url, boolean expected) {
            assertThat(PatternMatcher.matches(pattern, url)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
            "*.google.com, https://google.com, true",
            "*.example.org, https://example.org, true",
            "*.github.com, https://github.com/repo, true"
        })
        @DisplayName("wildcard pattern also matches bare domain")
        void wildcardMatchesBareDomain(String pattern, String url, boolean expected) {
            assertThat(PatternMatcher.matches(pattern, url)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
            "google.com, https://bing.com, false",
            "google.com, https://notgoogle.com, false",
            "google.com, https://google.org, false",
            "*.google.com, https://google.org, false",
            "example.org, https://example.com, false"
        })
        @DisplayName("pattern does not match different domains")
        void noMatchDifferentDomain(String pattern, String url, boolean expected) {
            assertThat(PatternMatcher.matches(pattern, url)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("matches() path patterns")
    class MatchesPathPatterns {

        @Test
        @DisplayName("double wildcard matches path segments")
        void doubleWildcardMatchesPath() {
            assertThat(PatternMatcher.matches("example.com/**", "https://example.com/path/to/file")).isTrue();
            assertThat(PatternMatcher.matches("example.com/api/**", "https://example.com/api/v1/users")).isTrue();
        }

        @Test
        @DisplayName("single wildcard in path matches segment")
        void singleWildcardInPath() {
            assertThat(PatternMatcher.matches("example.com/*.html", "https://example.com/page.html")).isTrue();
        }

        @Test
        @DisplayName("question mark matches single character")
        void questionMarkMatchesSingleChar() {
            assertThat(PatternMatcher.matches("example.co?", "https://example.com")).isTrue();
            assertThat(PatternMatcher.matches("exampl?.com", "https://example.com")).isTrue();
        }
    }

    @Nested
    @DisplayName("matches() edge cases")
    class MatchesEdgeCases {

        @Test
        @DisplayName("null inputs return false")
        void nullInputs() {
            assertThat(PatternMatcher.matches(null, "https://google.com")).isFalse();
            assertThat(PatternMatcher.matches("google.com", null)).isFalse();
            assertThat(PatternMatcher.matches(null, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidPattern()")
    class IsValidPattern {

        @ParameterizedTest
        @CsvSource({
            "google.com, true",
            "*.google.com, true",
            "**.google.com, true",
            "example.org/path/*, true",
            "https://example.com, true",
            "http://example.com, true",
            "?.google.com, true"
        })
        @DisplayName("returns true for valid patterns")
        void validPatterns(String pattern, boolean expected) {
            assertThat(PatternMatcher.isValidPattern(pattern)).isEqualTo(expected);
        }

        @Test
        @DisplayName("returns false for blank/null patterns")
        void invalidPatterns() {
            assertThat(PatternMatcher.isValidPattern(null)).isFalse();
            assertThat(PatternMatcher.isValidPattern("")).isFalse();
            assertThat(PatternMatcher.isValidPattern("   ")).isFalse();
        }

        @Test
        @DisplayName("returns false for wildcard-only patterns")
        void wildcardOnlyPatterns() {
            assertThat(PatternMatcher.isValidPattern("***")).isFalse();
            assertThat(PatternMatcher.isValidPattern("???")).isFalse();
            assertThat(PatternMatcher.isValidPattern("*?*")).isFalse();
        }
    }

    @Nested
    @DisplayName("domainToPattern()")
    class DomainToPattern {

        @Test
        @DisplayName("returns domain as-is (no conversion)")
        void returnsDomainAsIs() {
            assertThat(PatternMatcher.domainToPattern("google.com")).isEqualTo("google.com");
            assertThat(PatternMatcher.domainToPattern("mail.google.com")).isEqualTo("mail.google.com");
        }

        @Test
        @DisplayName("preserves existing wildcards")
        void preservesWildcards() {
            assertThat(PatternMatcher.domainToPattern("*.google.com")).isEqualTo("*.google.com");
            assertThat(PatternMatcher.domainToPattern("example.com/**")).isEqualTo("example.com/**");
        }

        @Test
        @DisplayName("handles blank input")
        void blankInput() {
            assertThat(PatternMatcher.domainToPattern(null)).isEqualTo("");
            assertThat(PatternMatcher.domainToPattern("")).isEqualTo("");
            assertThat(PatternMatcher.domainToPattern("   ")).isEqualTo("");
        }
    }
}
