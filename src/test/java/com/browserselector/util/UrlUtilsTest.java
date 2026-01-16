package com.browserselector.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UrlUtilsTest {

    @Nested
    @DisplayName("extractDomain()")
    class ExtractDomain {

        @ParameterizedTest
        @CsvSource({
            "https://google.com, google.com",
            "https://www.google.com, www.google.com",
            "http://google.com/search?q=test, google.com",
            "https://mail.google.com, mail.google.com",
            "https://example.org/page/sub, example.org"
        })
        @DisplayName("extracts domain from URL with scheme")
        void extractsDomainWithScheme(String url, String expected) {
            assertThat(UrlUtils.extractDomain(url)).isEqualTo(expected);
        }

        @Test
        @DisplayName("handles URL with port")
        void handlesPort() {
            assertThat(UrlUtils.extractDomain("https://localhost:8080/api")).isEqualTo("localhost");
            assertThat(UrlUtils.extractDomain("http://example.com:3000/path")).isEqualTo("example.com");
        }

        @Test
        @DisplayName("handles encoded URLs")
        void handlesEncodedUrls() {
            assertThat(UrlUtils.extractDomain("https://example.com/path%20with%20spaces")).isEqualTo("example.com");
        }

        @Test
        @DisplayName("returns lowercase domain")
        void returnsLowercase() {
            assertThat(UrlUtils.extractDomain("https://GOOGLE.COM/Path")).isEqualTo("google.com");
            assertThat(UrlUtils.extractDomain("https://Example.ORG")).isEqualTo("example.org");
        }

        @Test
        @DisplayName("handles blank/null input")
        void handlesBlankInput() {
            assertThat(UrlUtils.extractDomain(null)).isEqualTo("");
            assertThat(UrlUtils.extractDomain("")).isEqualTo("");
            assertThat(UrlUtils.extractDomain("   ")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("extractPath()")
    class ExtractPath {

        @ParameterizedTest
        @CsvSource({
            "https://example.com/path/to/file, /path/to/file",
            "https://example.com/, /",
            "https://example.com/api/v1/users, /api/v1/users"
        })
        @DisplayName("extracts path from URL")
        void extractsPath(String url, String expected) {
            assertThat(UrlUtils.extractPath(url)).isEqualTo(expected);
        }

        @Test
        @DisplayName("returns empty for URL without path")
        void emptyForNoPath() {
            assertThat(UrlUtils.extractPath("https://example.com")).isEmpty();
        }

        @Test
        @DisplayName("handles blank/null input")
        void handlesBlankInput() {
            assertThat(UrlUtils.extractPath(null)).isEqualTo("");
            assertThat(UrlUtils.extractPath("")).isEqualTo("");
            assertThat(UrlUtils.extractPath("   ")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("normalizeUrl()")
    class NormalizeUrl {

        @Test
        @DisplayName("adds https scheme when missing")
        void addsHttpsScheme() {
            assertThat(UrlUtils.normalizeUrl("google.com")).isEqualTo("https://google.com");
            assertThat(UrlUtils.normalizeUrl("example.org")).isEqualTo("https://example.org");
        }

        @Test
        @DisplayName("preserves existing https scheme")
        void preservesHttps() {
            assertThat(UrlUtils.normalizeUrl("https://google.com")).isEqualTo("https://google.com");
        }

        @Test
        @DisplayName("preserves existing http scheme")
        void preservesHttp() {
            assertThat(UrlUtils.normalizeUrl("http://google.com")).isEqualTo("http://google.com");
        }

        @Test
        @DisplayName("trims whitespace")
        void trimsWhitespace() {
            assertThat(UrlUtils.normalizeUrl("  google.com  ")).isEqualTo("https://google.com");
            assertThat(UrlUtils.normalizeUrl("  https://example.com  ")).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("handles null input")
        void handlesNullInput() {
            assertThat(UrlUtils.normalizeUrl(null)).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("isValidUrl()")
    class IsValidUrl {

        @ParameterizedTest
        @CsvSource({
            "https://google.com, true",
            "http://example.org, true",
            "https://example.com/path, true",
            "http://localhost:8080, true"
        })
        @DisplayName("returns true for valid URLs")
        void validUrls(String url, boolean expected) {
            assertThat(UrlUtils.isValidUrl(url)).isEqualTo(expected);
        }

        @Test
        @DisplayName("returns false for URLs without scheme")
        void falseForNoScheme() {
            assertThat(UrlUtils.isValidUrl("google.com")).isFalse();
            assertThat(UrlUtils.isValidUrl("example.org/path")).isFalse();
        }

        @Test
        @DisplayName("returns false for blank/null input")
        void falseForBlank() {
            assertThat(UrlUtils.isValidUrl(null)).isFalse();
            assertThat(UrlUtils.isValidUrl("")).isFalse();
            assertThat(UrlUtils.isValidUrl("   ")).isFalse();
        }

        @Test
        @DisplayName("returns false for non-http schemes")
        void falseForNonHttpSchemes() {
            assertThat(UrlUtils.isValidUrl("ftp://example.com")).isFalse();
            assertThat(UrlUtils.isValidUrl("file:///path/to/file")).isFalse();
        }
    }
}
