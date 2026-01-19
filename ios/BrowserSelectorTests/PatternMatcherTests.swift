import XCTest
@testable import BrowserSelector

final class PatternMatcherTests: XCTestCase {

    // MARK: - Domain Pattern Matching

    func testMatchesExactDomain() {
        XCTAssertTrue(PatternMatcher.matches(pattern: "google.com", url: "https://google.com"))
        XCTAssertTrue(PatternMatcher.matches(pattern: "google.com", url: "https://google.com/search"))
        XCTAssertTrue(PatternMatcher.matches(pattern: "google.com", url: "https://www.google.com"))
    }

    func testMatchesSubdomain() {
        XCTAssertTrue(PatternMatcher.matches(pattern: "google.com", url: "https://mail.google.com"))
        XCTAssertTrue(PatternMatcher.matches(pattern: "google.com", url: "https://docs.google.com/document"))
    }

    func testMatchesWildcardSubdomain() {
        XCTAssertTrue(PatternMatcher.matches(pattern: "*.google.com", url: "https://mail.google.com"))
        XCTAssertTrue(PatternMatcher.matches(pattern: "*.google.com", url: "https://docs.google.com"))
        // Wildcard pattern should also match bare domain
        XCTAssertTrue(PatternMatcher.matches(pattern: "*.google.com", url: "https://google.com"))
    }

    func testDoesNotMatchDifferentDomain() {
        XCTAssertFalse(PatternMatcher.matches(pattern: "google.com", url: "https://bing.com"))
        XCTAssertFalse(PatternMatcher.matches(pattern: "google.com", url: "https://notgoogle.com"))
    }

    // MARK: - Path Pattern Matching

    func testMatchesPathWildcard() {
        XCTAssertTrue(PatternMatcher.matches(pattern: "github.com/user/*", url: "https://github.com/user/repo"))
        XCTAssertTrue(PatternMatcher.matches(pattern: "github.com/user/*", url: "https://github.com/user/another"))
    }

    func testMatchesDoubleWildcard() {
        XCTAssertTrue(PatternMatcher.matches(pattern: "github.com/**", url: "https://github.com/user/repo/issues/123"))
    }

    // MARK: - Edge Cases

    func testEmptyPatternDoesNotMatch() {
        XCTAssertFalse(PatternMatcher.matches(pattern: "", url: "https://google.com"))
    }

    func testEmptyUrlDoesNotMatch() {
        XCTAssertFalse(PatternMatcher.matches(pattern: "google.com", url: ""))
    }

    func testCaseInsensitive() {
        XCTAssertTrue(PatternMatcher.matches(pattern: "Google.com", url: "https://GOOGLE.COM"))
        XCTAssertTrue(PatternMatcher.matches(pattern: "GITHUB.COM", url: "https://github.com"))
    }

    // MARK: - Pattern Validation

    func testIsValidPattern() {
        XCTAssertTrue(PatternMatcher.isValidPattern("google.com"))
        XCTAssertTrue(PatternMatcher.isValidPattern("*.google.com"))
        XCTAssertTrue(PatternMatcher.isValidPattern("github.com/user/*"))
        XCTAssertFalse(PatternMatcher.isValidPattern(""))
        XCTAssertFalse(PatternMatcher.isValidPattern("   "))
    }

    // MARK: - Domain Extraction

    func testExtractDomain() {
        XCTAssertEqual(PatternMatcher.extractDomain(from: "https://google.com"), "google.com")
        XCTAssertEqual(PatternMatcher.extractDomain(from: "https://www.google.com"), "google.com")
        XCTAssertEqual(PatternMatcher.extractDomain(from: "https://mail.google.com/inbox"), "mail.google.com")
    }

    // MARK: - URL Utilities

    func testNormalizeUrl() {
        XCTAssertEqual(PatternMatcher.normalizeUrl("google.com"), "https://google.com")
        XCTAssertEqual(PatternMatcher.normalizeUrl("http://google.com"), "http://google.com")
        XCTAssertEqual(PatternMatcher.normalizeUrl("https://google.com"), "https://google.com")
    }

    func testTruncateUrl() {
        let shortUrl = "https://g.co"
        XCTAssertEqual(PatternMatcher.truncateUrl(shortUrl, maxLength: 60), shortUrl)

        let longUrl = "https://example.com/very/long/path/that/exceeds/the/maximum/length/allowed"
        let truncated = PatternMatcher.truncateUrl(longUrl, maxLength: 30)
        XCTAssertTrue(truncated.hasSuffix("..."))
        XCTAssertEqual(truncated.count, 30)
    }

    func testDomainToPattern() {
        XCTAssertEqual(PatternMatcher.domainToPattern("www.google.com"), "google.com")
        XCTAssertEqual(PatternMatcher.domainToPattern("  google.com  "), "google.com")
    }
}
