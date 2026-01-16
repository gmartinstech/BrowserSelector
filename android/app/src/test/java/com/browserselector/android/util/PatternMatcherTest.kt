package com.browserselector.android.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PatternMatcher utility.
 */
class PatternMatcherTest {

    // ==================== Domain Pattern Tests ====================

    @Test
    fun `domain pattern matches exact domain`() {
        assertTrue(PatternMatcher.matches("google.com", "https://google.com"))
        assertTrue(PatternMatcher.matches("google.com", "http://google.com"))
        assertTrue(PatternMatcher.matches("google.com", "https://google.com/search"))
        assertTrue(PatternMatcher.matches("example.org", "https://example.org/page"))
    }

    @Test
    fun `domain pattern matches subdomain`() {
        assertTrue(PatternMatcher.matches("google.com", "https://www.google.com"))
        assertTrue(PatternMatcher.matches("google.com", "https://mail.google.com"))
        assertTrue(PatternMatcher.matches("google.com", "https://docs.google.com/document"))
        assertTrue(PatternMatcher.matches("example.org", "https://blog.example.org"))
    }

    @Test
    fun `wildcard pattern matches subdomain`() {
        assertTrue(PatternMatcher.matches("*.google.com", "https://mail.google.com"))
        assertTrue(PatternMatcher.matches("*.google.com", "https://docs.google.com"))
        assertTrue(PatternMatcher.matches("*.google.com", "https://www.google.com"))
        assertTrue(PatternMatcher.matches("*.example.org", "https://api.example.org/v1"))
    }

    @Test
    fun `wildcard pattern matches bare domain`() {
        // *.domain.com should also match domain.com (without subdomain)
        assertTrue(PatternMatcher.matches("*.google.com", "https://google.com"))
        assertTrue(PatternMatcher.matches("*.example.org", "https://example.org"))
        assertTrue(PatternMatcher.matches("*.github.com", "https://github.com/repo"))
    }

    @Test
    fun `pattern does not match different domain`() {
        assertFalse(PatternMatcher.matches("google.com", "https://bing.com"))
        assertFalse(PatternMatcher.matches("google.com", "https://notgoogle.com"))
        assertFalse(PatternMatcher.matches("google.com", "https://google.org"))
        assertFalse(PatternMatcher.matches("*.google.com", "https://google.org"))
        assertFalse(PatternMatcher.matches("example.org", "https://example.com"))
    }

    @Test
    fun `blank inputs return false`() {
        assertFalse(PatternMatcher.matches("", "https://google.com"))
        assertFalse(PatternMatcher.matches("google.com", ""))
        assertFalse(PatternMatcher.matches("", ""))
        assertFalse(PatternMatcher.matches("   ", "https://google.com"))
        assertFalse(PatternMatcher.matches("google.com", "   "))
        assertFalse(PatternMatcher.matches("  ", "  "))
    }

    // ==================== extractDomain Tests ====================

    @Test
    fun `extractDomain returns correct domain`() {
        assertEquals("google.com", PatternMatcher.extractDomain("https://google.com"))
        assertEquals("google.com", PatternMatcher.extractDomain("https://www.google.com"))
        assertEquals("google.com", PatternMatcher.extractDomain("http://google.com/search?q=test"))
        assertEquals("mail.google.com", PatternMatcher.extractDomain("https://mail.google.com"))
        assertEquals("example.org", PatternMatcher.extractDomain("https://example.org/page/sub"))
    }

    @Test
    fun `extractDomain handles urls without scheme`() {
        assertEquals("google.com", PatternMatcher.extractDomain("google.com"))
        assertEquals("google.com", PatternMatcher.extractDomain("www.google.com"))
    }

    // ==================== domainToPattern Tests ====================

    @Test
    fun `domainToPattern removes www prefix`() {
        assertEquals("google.com", PatternMatcher.domainToPattern("www.google.com"))
        assertEquals("example.org", PatternMatcher.domainToPattern("www.example.org"))
        assertEquals("github.com", PatternMatcher.domainToPattern("www.github.com"))
    }

    @Test
    fun `domainToPattern preserves domain without www`() {
        assertEquals("google.com", PatternMatcher.domainToPattern("google.com"))
        assertEquals("mail.google.com", PatternMatcher.domainToPattern("mail.google.com"))
    }

    @Test
    fun `domainToPattern trims whitespace`() {
        assertEquals("google.com", PatternMatcher.domainToPattern("  google.com  "))
        assertEquals("google.com", PatternMatcher.domainToPattern("  www.google.com  "))
    }

    // ==================== isValidPattern Tests ====================

    @Test
    fun `isValidPattern returns true for valid patterns`() {
        assertTrue(PatternMatcher.isValidPattern("google.com"))
        assertTrue(PatternMatcher.isValidPattern("*.google.com"))
        assertTrue(PatternMatcher.isValidPattern("**.google.com"))
        assertTrue(PatternMatcher.isValidPattern("example.org/path/*"))
        assertTrue(PatternMatcher.isValidPattern("https://example.com"))
        assertTrue(PatternMatcher.isValidPattern("http://example.com"))
        assertTrue(PatternMatcher.isValidPattern("?.google.com"))
    }

    @Test
    fun `isValidPattern returns false for blank patterns`() {
        assertFalse(PatternMatcher.isValidPattern(""))
        assertFalse(PatternMatcher.isValidPattern("   "))
    }

    // ==================== Additional Wildcard Tests ====================

    @Test
    fun `double wildcard matches path segments`() {
        assertTrue(PatternMatcher.matches("example.com/**", "https://example.com/path/to/file"))
        assertTrue(PatternMatcher.matches("example.com/api/**", "https://example.com/api/v1/users"))
    }

    @Test
    fun `single wildcard does not match path separator`() {
        // Single * should match within path segment only
        assertTrue(PatternMatcher.matches("example.com/*.html", "https://example.com/page.html"))
    }

    @Test
    fun `question mark matches single character`() {
        assertTrue(PatternMatcher.matches("example.co?", "https://example.com"))
        assertTrue(PatternMatcher.matches("exampl?.com", "https://example.com"))
    }

    // ==================== URL Normalization Tests ====================

    @Test
    fun `normalizeUrl adds https scheme when missing`() {
        assertEquals("https://google.com", PatternMatcher.normalizeUrl("google.com"))
        assertEquals("https://example.org", PatternMatcher.normalizeUrl("example.org"))
    }

    @Test
    fun `normalizeUrl preserves existing scheme`() {
        assertEquals("https://google.com", PatternMatcher.normalizeUrl("https://google.com"))
        assertEquals("http://google.com", PatternMatcher.normalizeUrl("http://google.com"))
    }

    @Test
    fun `normalizeUrl trims whitespace`() {
        assertEquals("https://google.com", PatternMatcher.normalizeUrl("  google.com  "))
    }

    // ==================== isValidUrl Tests ====================

    @Test
    fun `isValidUrl returns true for valid urls`() {
        assertTrue(PatternMatcher.isValidUrl("https://google.com"))
        assertTrue(PatternMatcher.isValidUrl("http://example.org"))
        assertTrue(PatternMatcher.isValidUrl("google.com"))
    }

    @Test
    fun `isValidUrl returns false for invalid urls`() {
        assertFalse(PatternMatcher.isValidUrl(""))
        assertFalse(PatternMatcher.isValidUrl("   "))
    }

    // ==================== truncateUrl Tests ====================

    @Test
    fun `truncateUrl returns full url when under limit`() {
        val shortUrl = "https://example.com"
        assertEquals(shortUrl, PatternMatcher.truncateUrl(shortUrl, 60))
    }

    @Test
    fun `truncateUrl truncates long urls with ellipsis`() {
        val longUrl = "https://example.com/very/long/path/to/some/deeply/nested/resource/file.html"
        val result = PatternMatcher.truncateUrl(longUrl, 30)
        assertEquals(30, result.length)
        assertTrue(result.endsWith("..."))
    }
}
