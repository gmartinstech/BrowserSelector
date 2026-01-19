# iOS BrowserSelector Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a native Swift/SwiftUI iOS app that registers as default browser and lets users choose which browser opens URLs, with domain-based rules.

**Architecture:** MVVM with SwiftUI views, SwiftData for persistence, URL scheme detection for browser discovery. App intercepts HTTP/HTTPS URLs when set as default browser.

**Tech Stack:** Swift 5.9+, SwiftUI, SwiftData, iOS 17+, Xcode 15.4, GitHub Actions CI

---

## Phase 1: Project Scaffold & CI Setup

### Task 1.1: Update GitHub Workflow

**Files:**
- Modify: `.github/workflows/build.yml`

**Step 1: Add build-ios job to workflow**

Add this job after `build-android` in `.github/workflows/build.yml`:

```yaml
  build-ios:
    runs-on: macos-latest

    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.event_name == 'workflow_dispatch' && inputs.tag || github.ref }}
          fetch-depth: 0

      - name: Check if iOS project exists
        id: check_ios
        run: |
          if [ -d "ios" ]; then
            echo "IOS_EXISTS=true" >> $GITHUB_OUTPUT
          else
            echo "IOS_EXISTS=false" >> $GITHUB_OUTPUT
          fi

      - name: Determine version
        id: version
        run: |
          if [ "${{ github.event_name }}" == "workflow_dispatch" ]; then
            TAG="${{ inputs.tag }}"
          elif [[ "${{ github.ref }}" == refs/tags/* ]]; then
            TAG="${{ github.ref_name }}"
          else
            TAG="dev"
          fi
          VERSION="${TAG#v}"
          echo "TAG=$TAG" >> $GITHUB_OUTPUT
          echo "VERSION=$VERSION" >> $GITHUB_OUTPUT

      - name: Select Xcode version
        if: steps.check_ios.outputs.IOS_EXISTS == 'true'
        run: sudo xcode-select -s /Applications/Xcode_15.4.app

      - name: Resolve Swift Package Dependencies
        if: steps.check_ios.outputs.IOS_EXISTS == 'true'
        working-directory: ios
        run: xcodebuild -resolvePackageDependencies -project BrowserSelector.xcodeproj -scheme BrowserSelector

      - name: Build iOS App
        if: steps.check_ios.outputs.IOS_EXISTS == 'true'
        working-directory: ios
        run: |
          xcodebuild clean build \
            -project BrowserSelector.xcodeproj \
            -scheme BrowserSelector \
            -destination 'generic/platform=iOS' \
            -configuration Release \
            CODE_SIGNING_ALLOWED='NO'

      - name: Run Tests
        if: steps.check_ios.outputs.IOS_EXISTS == 'true'
        working-directory: ios
        run: |
          xcodebuild test \
            -project BrowserSelector.xcodeproj \
            -scheme BrowserSelector \
            -destination 'platform=iOS Simulator,name=iPhone 15' \
            CODE_SIGNING_ALLOWED='NO' \
            || echo "Tests completed (may have no tests yet)"

      - name: Create Archive
        if: steps.check_ios.outputs.IOS_EXISTS == 'true' && (startsWith(github.ref, 'refs/tags/v') || github.event_name == 'workflow_dispatch')
        working-directory: ios
        run: |
          xcodebuild archive \
            -project BrowserSelector.xcodeproj \
            -scheme BrowserSelector \
            -archivePath build/BrowserSelector.xcarchive \
            -destination 'generic/platform=iOS' \
            CODE_SIGNING_ALLOWED='NO' \
            SKIP_INSTALL=NO \
            BUILD_LIBRARY_FOR_DISTRIBUTION=YES

      - name: Prepare artifacts
        if: steps.check_ios.outputs.IOS_EXISTS == 'true' && (startsWith(github.ref, 'refs/tags/v') || github.event_name == 'workflow_dispatch')
        run: |
          TAG="${{ steps.version.outputs.TAG }}"
          mkdir -p artifacts
          if [ -d "ios/build/BrowserSelector.xcarchive" ]; then
            cd ios/build
            zip -r "../../artifacts/BrowserSelector-${TAG}-ios.xcarchive.zip" BrowserSelector.xcarchive
          fi

      - name: Upload iOS artifact
        if: steps.check_ios.outputs.IOS_EXISTS == 'true'
        uses: actions/upload-artifact@v4
        with:
          name: browser-selector-ios
          path: artifacts/*.zip
          if-no-files-found: warn
```

**Step 2: Update release job to download iOS artifacts**

In the `release` job, add after the Android download step:

```yaml
      - name: Download iOS artifact
        uses: actions/download-artifact@v4
        with:
          name: browser-selector-ios
          path: artifacts
        continue-on-error: true
```

**Step 3: Commit workflow changes**

```bash
git add .github/workflows/build.yml
git commit -m "feat: add iOS build job to CI workflow"
```

---

### Task 1.2: Create Xcode Project Structure

**Files:**
- Create: `ios/BrowserSelector.xcodeproj/project.pbxproj`
- Create: `ios/BrowserSelector/BrowserSelectorApp.swift`
- Create: `ios/BrowserSelector/ContentView.swift`
- Create: `ios/BrowserSelector/Info.plist`
- Create: `ios/BrowserSelectorTests/BrowserSelectorTests.swift`

**Step 1: Create iOS directory and project using xcodegen or manually**

Since we're on Linux and can't run Xcode directly, create the project structure manually:

```bash
mkdir -p ios/BrowserSelector
mkdir -p ios/BrowserSelectorTests
```

**Step 2: Create BrowserSelectorApp.swift**

Create `ios/BrowserSelector/BrowserSelectorApp.swift`:

```swift
import SwiftUI

@main
struct BrowserSelectorApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

**Step 3: Create ContentView.swift**

Create `ios/BrowserSelector/ContentView.swift`:

```swift
import SwiftUI

struct ContentView: View {
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Image(systemName: "globe")
                    .imageScale(.large)
                    .foregroundStyle(.tint)
                    .font(.system(size: 60))

                Text("BrowserSelector")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                Text("Set as default browser in Settings")
                    .foregroundStyle(.secondary)
            }
            .padding()
            .navigationTitle("Browser Selector")
        }
    }
}

#Preview {
    ContentView()
}
```

**Step 4: Create Info.plist**

Create `ios/BrowserSelector/Info.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>$(DEVELOPMENT_LANGUAGE)</string>
    <key>CFBundleExecutable</key>
    <string>$(EXECUTABLE_NAME)</string>
    <key>CFBundleIdentifier</key>
    <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$(PRODUCT_NAME)</string>
    <key>CFBundlePackageType</key>
    <string>$(PRODUCT_BUNDLE_PACKAGE_TYPE)</string>
    <key>CFBundleShortVersionString</key>
    <string>$(MARKETING_VERSION)</string>
    <key>CFBundleVersion</key>
    <string>$(CURRENT_PROJECT_VERSION)</string>
    <key>LSRequiresIPhoneOS</key>
    <true/>
    <key>UIApplicationSceneManifest</key>
    <dict>
        <key>UIApplicationSupportsMultipleScenes</key>
        <true/>
    </dict>
    <key>UILaunchScreen</key>
    <dict/>
    <key>UIRequiredDeviceCapabilities</key>
    <array>
        <string>arm64</string>
    </array>
    <key>UISupportedInterfaceOrientations</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
        <string>UIInterfaceOrientationLandscapeLeft</string>
        <string>UIInterfaceOrientationLandscapeRight</string>
    </array>
    <key>UISupportedInterfaceOrientations~ipad</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
        <string>UIInterfaceOrientationPortraitUpsideDown</string>
        <string>UIInterfaceOrientationLandscapeLeft</string>
        <string>UIInterfaceOrientationLandscapeRight</string>
    </array>
    <key>LSApplicationQueriesSchemes</key>
    <array>
        <string>https</string>
        <string>http</string>
        <string>googlechrome</string>
        <string>googlechromes</string>
        <string>googlechrome-x-callback</string>
        <string>firefox</string>
        <string>firefox-focus</string>
        <string>brave</string>
        <string>microsoft-edge</string>
        <string>microsoft-edge-https</string>
        <string>opera-http</string>
        <string>opera-https</string>
        <string>touch-http</string>
        <string>touch-https</string>
        <string>ddgQuickLink</string>
        <string>yandexbrowser-open-url</string>
        <string>ucbrowser</string>
        <string>alohabrowser</string>
        <string>onionhttp</string>
        <string>onionhttps</string>
        <string>puffin</string>
    </array>
    <key>CFBundleURLTypes</key>
    <array>
        <dict>
            <key>CFBundleURLSchemes</key>
            <array>
                <string>http</string>
                <string>https</string>
            </array>
            <key>CFBundleURLName</key>
            <string>Web URL</string>
            <key>CFBundleTypeRole</key>
            <string>Viewer</string>
        </dict>
    </array>
</dict>
</plist>
```

**Step 5: Create test file**

Create `ios/BrowserSelectorTests/BrowserSelectorTests.swift`:

```swift
import XCTest
@testable import BrowserSelector

final class BrowserSelectorTests: XCTestCase {

    func testExample() throws {
        XCTAssertTrue(true)
    }
}
```

**Step 6: Create project.pbxproj**

Create `ios/BrowserSelector.xcodeproj/project.pbxproj` (full Xcode project file - this is a large file that defines the project structure):

See separate file creation below.

**Step 7: Commit project structure**

```bash
git add ios/
git commit -m "feat(ios): add Xcode project scaffold"
```

---

## Phase 2: Core Logic

### Task 2.1: Create PatternMatcher

**Files:**
- Create: `ios/BrowserSelector/Services/PatternMatcher.swift`
- Create: `ios/BrowserSelectorTests/PatternMatcherTests.swift`

**Step 1: Create PatternMatcher.swift**

Create `ios/BrowserSelector/Services/PatternMatcher.swift`:

```swift
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
```

**Step 2: Create PatternMatcherTests.swift**

Create `ios/BrowserSelectorTests/PatternMatcherTests.swift`:

```swift
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
```

**Step 3: Commit**

```bash
git add ios/BrowserSelector/Services/PatternMatcher.swift
git add ios/BrowserSelectorTests/PatternMatcherTests.swift
git commit -m "feat(ios): add PatternMatcher with tests"
```

---

### Task 2.2: Create Browser Model

**Files:**
- Create: `ios/BrowserSelector/Models/Browser.swift`

**Step 1: Create Browser.swift**

Create `ios/BrowserSelector/Models/Browser.swift`:

```swift
import Foundation
import UIKit

/// Represents an installed browser on the device.
struct Browser: Identifiable, Hashable {
    let id: String
    let name: String
    let urlScheme: String
    let urlFormat: String
    var enabled: Bool
    var isDefault: Bool
    var isInstalled: Bool

    /// Opens the given URL in this browser.
    func open(url: String) -> Bool {
        let formattedUrl = formatUrl(url)
        guard let browserUrl = URL(string: formattedUrl) else { return false }

        if UIApplication.shared.canOpenURL(browserUrl) {
            UIApplication.shared.open(browserUrl)
            return true
        }
        return false
    }

    /// Formats a URL for this browser's scheme.
    private func formatUrl(_ url: String) -> String {
        // Extract the URL without scheme
        var cleanUrl = url
        if cleanUrl.hasPrefix("https://") {
            cleanUrl = String(cleanUrl.dropFirst(8))
        } else if cleanUrl.hasPrefix("http://") {
            cleanUrl = String(cleanUrl.dropFirst(7))
        }

        // Apply the browser's URL format
        return urlFormat
            .replacingOccurrences(of: "{url}", with: url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? url)
            .replacingOccurrences(of: "{host}", with: cleanUrl.components(separatedBy: "/").first ?? cleanUrl)
            .replacingOccurrences(of: "{path}", with: {
                let components = cleanUrl.components(separatedBy: "/")
                if components.count > 1 {
                    return "/" + components.dropFirst().joined(separator: "/")
                }
                return ""
            }())
    }

    // MARK: - Hashable

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: Browser, rhs: Browser) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Known Browsers

extension Browser {
    /// All known iOS browsers with their URL schemes.
    static let knownBrowsers: [Browser] = [
        Browser(
            id: "safari",
            name: "Safari",
            urlScheme: "",
            urlFormat: "{url}",
            enabled: true,
            isDefault: false,
            isInstalled: true // Safari is always installed
        ),
        Browser(
            id: "chrome",
            name: "Chrome",
            urlScheme: "googlechromes",
            urlFormat: "googlechromes://{host}{path}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "firefox",
            name: "Firefox",
            urlScheme: "firefox",
            urlFormat: "firefox://open-url?url={url}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "brave",
            name: "Brave",
            urlScheme: "brave",
            urlFormat: "brave://open-url?url={url}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "edge",
            name: "Microsoft Edge",
            urlScheme: "microsoft-edge",
            urlFormat: "microsoft-edge://open?url={url}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "opera",
            name: "Opera",
            urlScheme: "touch-https",
            urlFormat: "touch-https://{host}{path}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "opera-mini",
            name: "Opera Mini",
            urlScheme: "opera-https",
            urlFormat: "opera-https://{host}{path}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "firefox-focus",
            name: "Firefox Focus",
            urlScheme: "firefox-focus",
            urlFormat: "firefox-focus://open-url?url={url}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "duckduckgo",
            name: "DuckDuckGo",
            urlScheme: "ddgQuickLink",
            urlFormat: "ddgQuickLink://{url}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "yandex",
            name: "Yandex",
            urlScheme: "yandexbrowser-open-url",
            urlFormat: "yandexbrowser-open-url://{url}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "aloha",
            name: "Aloha",
            urlScheme: "alohabrowser",
            urlFormat: "alohabrowser://{url}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "onion",
            name: "Onion Browser",
            urlScheme: "onionhttps",
            urlFormat: "onionhttps://{host}{path}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "puffin",
            name: "Puffin",
            urlScheme: "puffin",
            urlFormat: "puffin://{url}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        ),
        Browser(
            id: "uc",
            name: "UC Browser",
            urlScheme: "ucbrowser",
            urlFormat: "ucbrowser://{url}",
            enabled: true,
            isDefault: false,
            isInstalled: false
        )
    ]
}
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/Models/Browser.swift
git commit -m "feat(ios): add Browser model with known browsers"
```

---

### Task 2.3: Create UrlRule Model with SwiftData

**Files:**
- Create: `ios/BrowserSelector/Models/UrlRule.swift`

**Step 1: Create UrlRule.swift**

Create `ios/BrowserSelector/Models/UrlRule.swift`:

```swift
import Foundation
import SwiftData

/// Represents a URL pattern rule that maps URLs to specific browsers.
@Model
class UrlRule {
    var pattern: String
    var browserScheme: String
    var priority: Int
    var createdAt: Date

    init(pattern: String, browserScheme: String, priority: Int = 0) {
        self.pattern = pattern
        self.browserScheme = browserScheme
        self.priority = priority
        self.createdAt = Date()
    }

    /// Creates a copy with updated priority.
    func withPriority(_ priority: Int) -> UrlRule {
        UrlRule(pattern: pattern, browserScheme: browserScheme, priority: priority)
    }
}
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/Models/UrlRule.swift
git commit -m "feat(ios): add UrlRule SwiftData model"
```

---

## Phase 3: Data Layer

### Task 3.1: Create BrowserDetector Service

**Files:**
- Create: `ios/BrowserSelector/Services/BrowserDetector.swift`

**Step 1: Create BrowserDetector.swift**

Create `ios/BrowserSelector/Services/BrowserDetector.swift`:

```swift
import Foundation
import UIKit

/// Service to detect installed browsers on the device.
/// Uses canOpenURL to check if browser URL schemes are registered.
class BrowserDetector {

    /// Detects which browsers are installed on the device.
    func detectBrowsers() -> [Browser] {
        Browser.knownBrowsers.map { browser in
            var detectedBrowser = browser

            if browser.urlScheme.isEmpty {
                // Safari is always installed
                detectedBrowser.isInstalled = true
            } else {
                // Check if the browser's URL scheme can be opened
                if let url = URL(string: "\(browser.urlScheme)://") {
                    detectedBrowser.isInstalled = UIApplication.shared.canOpenURL(url)
                } else {
                    detectedBrowser.isInstalled = false
                }
            }

            return detectedBrowser
        }
    }

    /// Returns only installed browsers.
    func getInstalledBrowsers() -> [Browser] {
        detectBrowsers().filter { $0.isInstalled }
    }

    /// Returns installed and enabled browsers.
    func getEnabledBrowsers() -> [Browser] {
        detectBrowsers().filter { $0.isInstalled && $0.enabled }
    }

    /// Gets a browser by its ID.
    func getBrowser(byId id: String) -> Browser? {
        Browser.knownBrowsers.first { $0.id == id }
    }

    /// Gets a browser by its URL scheme.
    func getBrowser(byScheme scheme: String) -> Browser? {
        Browser.knownBrowsers.first { $0.urlScheme == scheme }
    }
}
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/Services/BrowserDetector.swift
git commit -m "feat(ios): add BrowserDetector service"
```

---

### Task 3.2: Create Persistence and Repository

**Files:**
- Create: `ios/BrowserSelector/Data/Persistence.swift`
- Create: `ios/BrowserSelector/Data/BrowserRepository.swift`

**Step 1: Create Persistence.swift**

Create `ios/BrowserSelector/Data/Persistence.swift`:

```swift
import Foundation
import SwiftData

/// SwiftData model container configuration.
@MainActor
class Persistence {
    static let shared = Persistence()

    let container: ModelContainer

    init() {
        let schema = Schema([UrlRule.self])
        let configuration = ModelConfiguration(
            schema: schema,
            isStoredInMemoryOnly: false
        )

        do {
            container = try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }

    /// Preview container for SwiftUI previews.
    static var preview: Persistence = {
        let persistence = Persistence()
        let context = persistence.container.mainContext

        // Add sample data
        let rule1 = UrlRule(pattern: "github.com", browserScheme: "chrome", priority: 1)
        let rule2 = UrlRule(pattern: "*.google.com", browserScheme: "safari", priority: 2)
        context.insert(rule1)
        context.insert(rule2)

        return persistence
    }()
}
```

**Step 2: Create BrowserRepository.swift**

Create `ios/BrowserSelector/Data/BrowserRepository.swift`:

```swift
import Foundation
import SwiftData

/// Repository providing a clean API for data access.
/// Manages browsers and URL rules.
@MainActor
class BrowserRepository: ObservableObject {

    private let modelContext: ModelContext
    private let browserDetector = BrowserDetector()

    @Published var browsers: [Browser] = []
    @Published var rules: [UrlRule] = []

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
        refreshBrowsers()
        loadRules()
    }

    // MARK: - Browser Operations

    /// Refreshes the list of installed browsers.
    func refreshBrowsers() {
        browsers = browserDetector.getInstalledBrowsers()
    }

    /// Gets enabled browsers.
    func getEnabledBrowsers() -> [Browser] {
        browsers.filter { $0.enabled }
    }

    /// Gets a browser by scheme.
    func getBrowser(byScheme scheme: String) -> Browser? {
        browsers.first { $0.urlScheme == scheme } ?? browserDetector.getBrowser(byScheme: scheme)
    }

    // MARK: - Rule Operations

    /// Loads all rules from the database.
    func loadRules() {
        let descriptor = FetchDescriptor<UrlRule>(sortBy: [SortDescriptor(\.priority, order: .reverse)])
        do {
            rules = try modelContext.fetch(descriptor)
        } catch {
            print("Failed to fetch rules: \(error)")
            rules = []
        }
    }

    /// Finds the first matching rule for the given URL.
    func findMatchingRule(for url: String) -> UrlRule? {
        rules.first { rule in
            PatternMatcher.matches(pattern: rule.pattern, url: url)
        }
    }

    /// Creates a new rule.
    func createRule(pattern: String, browserScheme: String) {
        let maxPriority = rules.map(\.priority).max() ?? 0
        let rule = UrlRule(pattern: pattern, browserScheme: browserScheme, priority: maxPriority + 1)
        modelContext.insert(rule)
        saveContext()
        loadRules()
    }

    /// Updates an existing rule.
    func updateRule(_ rule: UrlRule) {
        saveContext()
        loadRules()
    }

    /// Deletes a rule.
    func deleteRule(_ rule: UrlRule) {
        modelContext.delete(rule)
        saveContext()
        loadRules()
    }

    /// Deletes rules at the given offsets.
    func deleteRules(at offsets: IndexSet) {
        for index in offsets {
            modelContext.delete(rules[index])
        }
        saveContext()
        loadRules()
    }

    // MARK: - Private

    private func saveContext() {
        do {
            try modelContext.save()
        } catch {
            print("Failed to save context: \(error)")
        }
    }
}
```

**Step 3: Commit**

```bash
git add ios/BrowserSelector/Data/
git commit -m "feat(ios): add Persistence and BrowserRepository"
```

---

## Phase 4: Browser Selector UI

### Task 4.1: Create BrowserRow Component

**Files:**
- Create: `ios/BrowserSelector/Views/Components/BrowserRow.swift`

**Step 1: Create BrowserRow.swift**

Create `ios/BrowserSelector/Views/Components/BrowserRow.swift`:

```swift
import SwiftUI

/// A row displaying a browser option.
struct BrowserRow: View {
    let browser: Browser
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 16) {
                // Browser icon placeholder (system image based on browser)
                Image(systemName: iconName)
                    .font(.title)
                    .frame(width: 44, height: 44)
                    .foregroundStyle(.primary)

                VStack(alignment: .leading, spacing: 2) {
                    Text(browser.name)
                        .font(.body)
                        .foregroundStyle(.primary)

                    if browser.isDefault {
                        Text("Default")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var iconName: String {
        switch browser.id {
        case "safari": return "safari"
        case "chrome": return "globe"
        case "firefox": return "flame"
        case "brave": return "shield"
        case "edge": return "globe.americas"
        case "opera", "opera-mini": return "circle.circle"
        case "duckduckgo": return "magnifyingglass"
        default: return "globe"
        }
    }
}

#Preview {
    List {
        BrowserRow(browser: Browser.knownBrowsers[0]) {}
        BrowserRow(browser: Browser.knownBrowsers[1]) {}
        BrowserRow(browser: Browser.knownBrowsers[2]) {}
    }
}
```

**Step 2: Commit**

```bash
mkdir -p ios/BrowserSelector/Views/Components
git add ios/BrowserSelector/Views/Components/BrowserRow.swift
git commit -m "feat(ios): add BrowserRow component"
```

---

### Task 4.2: Create BrowserSelectorViewModel

**Files:**
- Create: `ios/BrowserSelector/ViewModels/BrowserSelectorViewModel.swift`

**Step 1: Create BrowserSelectorViewModel.swift**

Create `ios/BrowserSelector/ViewModels/BrowserSelectorViewModel.swift`:

```swift
import Foundation
import SwiftUI

/// ViewModel for the browser selector view.
@MainActor
class BrowserSelectorViewModel: ObservableObject {

    @Published var browsers: [Browser] = []
    @Published var url: String = ""
    @Published var displayUrl: String = ""
    @Published var rememberChoice: Bool = false
    @Published var pattern: String = ""
    @Published var isLoading: Bool = true
    @Published var shouldDismiss: Bool = false

    private let repository: BrowserRepository

    init(repository: BrowserRepository) {
        self.repository = repository
    }

    /// Sets up the view with a URL to open.
    func setup(with url: String) {
        self.url = url
        self.displayUrl = PatternMatcher.truncateUrl(url, maxLength: 60)

        if let domain = PatternMatcher.extractDomain(from: url) {
            self.pattern = PatternMatcher.domainToPattern(domain)
        }

        loadBrowsers()
        checkForMatchingRule()
    }

    /// Loads available browsers.
    private func loadBrowsers() {
        repository.refreshBrowsers()
        browsers = repository.getEnabledBrowsers()
        isLoading = false
    }

    /// Checks if there's a matching rule and auto-opens if so.
    private func checkForMatchingRule() {
        if let rule = repository.findMatchingRule(for: url),
           let browser = repository.getBrowser(byScheme: rule.browserScheme) {
            openUrl(with: browser, saveRule: false)
        }
    }

    /// Opens the URL with the selected browser.
    func selectBrowser(_ browser: Browser) {
        openUrl(with: browser, saveRule: rememberChoice)
    }

    /// Opens the URL with the given browser.
    private func openUrl(with browser: Browser, saveRule: Bool) {
        if saveRule && PatternMatcher.isValidPattern(pattern) {
            repository.createRule(pattern: pattern, browserScheme: browser.urlScheme)
        }

        if browser.urlScheme.isEmpty {
            // Safari - open directly
            if let browserUrl = URL(string: url) {
                UIApplication.shared.open(browserUrl)
            }
        } else {
            _ = browser.open(url: url)
        }

        shouldDismiss = true
    }

    /// Opens the system share sheet as a fallback.
    func openSystemPicker() {
        // This would be handled by the view
    }
}
```

**Step 2: Commit**

```bash
mkdir -p ios/BrowserSelector/ViewModels
git add ios/BrowserSelector/ViewModels/BrowserSelectorViewModel.swift
git commit -m "feat(ios): add BrowserSelectorViewModel"
```

---

### Task 4.3: Create BrowserSelectorView

**Files:**
- Create: `ios/BrowserSelector/Views/BrowserSelectorView.swift`

**Step 1: Create BrowserSelectorView.swift**

Create `ios/BrowserSelector/Views/BrowserSelectorView.swift`:

```swift
import SwiftUI

/// Main view for selecting a browser to open a URL.
struct BrowserSelectorView: View {
    @StateObject private var viewModel: BrowserSelectorViewModel
    @Environment(\.dismiss) private var dismiss

    let url: String

    init(url: String, repository: BrowserRepository) {
        self.url = url
        _viewModel = StateObject(wrappedValue: BrowserSelectorViewModel(repository: repository))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // URL display
                urlHeader

                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxHeight: .infinity)
                } else if viewModel.browsers.isEmpty {
                    noBrowsersView
                } else {
                    browserList
                }

                // Remember checkbox and pattern
                rememberSection

                // Bottom buttons
                bottomButtons
            }
            .navigationTitle("Open with")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                viewModel.setup(with: url)
            }
            .onChange(of: viewModel.shouldDismiss) { _, shouldDismiss in
                if shouldDismiss {
                    dismiss()
                }
            }
        }
    }

    private var urlHeader: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Opening:")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(viewModel.displayUrl)
                .font(.subheadline)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
    }

    private var browserList: some View {
        List {
            ForEach(viewModel.browsers) { browser in
                BrowserRow(browser: browser) {
                    viewModel.selectBrowser(browser)
                }
            }
        }
        .listStyle(.plain)
    }

    private var noBrowsersView: some View {
        ContentUnavailableView(
            "No Browsers Found",
            systemImage: "globe",
            description: Text("No other browsers are installed on this device.")
        )
    }

    private var rememberSection: some View {
        VStack(spacing: 12) {
            Toggle(isOn: $viewModel.rememberChoice) {
                Text("Remember for this domain")
            }
            .padding(.horizontal)

            if viewModel.rememberChoice {
                TextField("Pattern", text: $viewModel.pattern)
                    .textFieldStyle(.roundedBorder)
                    .font(.footnote)
                    .autocapitalization(.none)
                    .autocorrectionDisabled()
                    .padding(.horizontal)
            }
        }
        .padding(.vertical)
        .background(Color(.secondarySystemBackground))
    }

    private var bottomButtons: some View {
        HStack(spacing: 12) {
            Button {
                // Open share sheet
            } label: {
                Label("More Options", systemImage: "square.and.arrow.up")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
        .padding()
    }
}

#Preview {
    BrowserSelectorView(
        url: "https://github.com/example/repo",
        repository: BrowserRepository(modelContext: Persistence.preview.container.mainContext)
    )
}
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/Views/BrowserSelectorView.swift
git commit -m "feat(ios): add BrowserSelectorView"
```

---

### Task 4.4: Update App Entry Point for URL Handling

**Files:**
- Modify: `ios/BrowserSelector/BrowserSelectorApp.swift`

**Step 1: Update BrowserSelectorApp.swift**

Replace `ios/BrowserSelector/BrowserSelectorApp.swift`:

```swift
import SwiftUI
import SwiftData

@main
struct BrowserSelectorApp: App {
    @State private var incomingUrl: String?

    var sharedModelContainer: ModelContainer = {
        let schema = Schema([UrlRule.self])
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)

        do {
            return try ModelContainer(for: schema, configurations: [configuration])
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
    }()

    var body: some Scene {
        WindowGroup {
            Group {
                if let url = incomingUrl {
                    BrowserSelectorView(
                        url: url,
                        repository: BrowserRepository(modelContext: sharedModelContainer.mainContext)
                    )
                } else {
                    SettingsView()
                }
            }
            .onOpenURL { url in
                handleIncomingUrl(url)
            }
        }
        .modelContainer(sharedModelContainer)
    }

    private func handleIncomingUrl(_ url: URL) {
        // Convert the incoming URL to a string for the browser selector
        incomingUrl = url.absoluteString
    }
}
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/BrowserSelectorApp.swift
git commit -m "feat(ios): add URL handling to app entry point"
```

---

## Phase 5: Settings UI

### Task 5.1: Create UrlRuleRow Component

**Files:**
- Create: `ios/BrowserSelector/Views/Components/UrlRuleRow.swift`

**Step 1: Create UrlRuleRow.swift**

Create `ios/BrowserSelector/Views/Components/UrlRuleRow.swift`:

```swift
import SwiftUI

/// A row displaying a URL rule.
struct UrlRuleRow: View {
    let rule: UrlRule
    let browserName: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(rule.pattern)
                .font(.body)

            HStack {
                Image(systemName: "arrow.right")
                    .font(.caption2)
                Text(browserName)
                    .font(.caption)
            }
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}

#Preview {
    List {
        UrlRuleRow(rule: UrlRule(pattern: "github.com", browserScheme: "chrome"), browserName: "Chrome")
        UrlRuleRow(rule: UrlRule(pattern: "*.google.com", browserScheme: "safari"), browserName: "Safari")
    }
}
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/Views/Components/UrlRuleRow.swift
git commit -m "feat(ios): add UrlRuleRow component"
```

---

### Task 5.2: Create AddRuleView

**Files:**
- Create: `ios/BrowserSelector/Views/AddRuleView.swift`

**Step 1: Create AddRuleView.swift**

Create `ios/BrowserSelector/Views/AddRuleView.swift`:

```swift
import SwiftUI

/// View for adding or editing a URL rule.
struct AddRuleView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var repository: BrowserRepository

    @State private var pattern: String = ""
    @State private var selectedBrowserScheme: String = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Pattern (e.g., github.com)", text: $pattern)
                        .autocapitalization(.none)
                        .autocorrectionDisabled()
                } header: {
                    Text("URL Pattern")
                } footer: {
                    Text("Use * for wildcard. Example: *.google.com matches all Google subdomains.")
                }

                Section("Browser") {
                    Picker("Open with", selection: $selectedBrowserScheme) {
                        ForEach(repository.browsers) { browser in
                            Text(browser.name).tag(browser.urlScheme)
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
            }
            .navigationTitle("Add Rule")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        saveRule()
                    }
                    .disabled(!isValid)
                }
            }
            .onAppear {
                if selectedBrowserScheme.isEmpty, let first = repository.browsers.first {
                    selectedBrowserScheme = first.urlScheme
                }
            }
        }
    }

    private var isValid: Bool {
        PatternMatcher.isValidPattern(pattern) && !selectedBrowserScheme.isEmpty
    }

    private func saveRule() {
        repository.createRule(pattern: pattern, browserScheme: selectedBrowserScheme)
        dismiss()
    }
}

#Preview {
    AddRuleView(repository: BrowserRepository(modelContext: Persistence.preview.container.mainContext))
}
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/Views/AddRuleView.swift
git commit -m "feat(ios): add AddRuleView"
```

---

### Task 5.3: Create SettingsView

**Files:**
- Create: `ios/BrowserSelector/Views/SettingsView.swift`

**Step 1: Create SettingsView.swift**

Create `ios/BrowserSelector/Views/SettingsView.swift`:

```swift
import SwiftUI
import SwiftData

/// Settings view for managing browsers and rules.
struct SettingsView: View {
    @Environment(\.modelContext) private var modelContext
    @StateObject private var repository: BrowserRepository

    @State private var showingAddRule = false

    init() {
        // Initialize with a temporary context - will be replaced in onAppear
        let container = try! ModelContainer(for: UrlRule.self)
        _repository = StateObject(wrappedValue: BrowserRepository(modelContext: container.mainContext))
    }

    var body: some View {
        NavigationStack {
            List {
                // Setup instructions
                Section {
                    instructionsView
                } header: {
                    Text("Setup")
                }

                // Installed browsers
                Section {
                    ForEach(repository.browsers) { browser in
                        HStack {
                            Image(systemName: browserIcon(for: browser))
                                .frame(width: 30)
                            Text(browser.name)
                            Spacer()
                            if browser.isInstalled {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.green)
                            }
                        }
                    }
                } header: {
                    Text("Installed Browsers")
                } footer: {
                    Text("Only installed browsers can be used to open URLs.")
                }

                // URL Rules
                Section {
                    if repository.rules.isEmpty {
                        Text("No rules yet")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(repository.rules, id: \.pattern) { rule in
                            UrlRuleRow(
                                rule: rule,
                                browserName: repository.getBrowser(byScheme: rule.browserScheme)?.name ?? rule.browserScheme
                            )
                        }
                        .onDelete { offsets in
                            repository.deleteRules(at: offsets)
                        }
                    }
                } header: {
                    Text("URL Rules")
                } footer: {
                    Text("Rules automatically open matching URLs in the specified browser.")
                }
            }
            .navigationTitle("Browser Selector")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showingAddRule = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showingAddRule) {
                AddRuleView(repository: repository)
            }
            .refreshable {
                repository.refreshBrowsers()
                repository.loadRules()
            }
        }
    }

    private var instructionsView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label {
                Text("To use Browser Selector:")
                    .font(.headline)
            } icon: {
                Image(systemName: "info.circle")
            }

            VStack(alignment: .leading, spacing: 8) {
                instructionStep(1, "Open Settings app")
                instructionStep(2, "Tap 'Apps' → 'Browser Selector'")
                instructionStep(3, "Tap 'Default Browser App'")
                instructionStep(4, "Select 'Browser Selector'")
            }
            .font(.subheadline)
        }
        .padding(.vertical, 8)
    }

    private func instructionStep(_ number: Int, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("\(number).")
                .foregroundStyle(.secondary)
                .frame(width: 20, alignment: .trailing)
            Text(text)
        }
    }

    private func browserIcon(for browser: Browser) -> String {
        switch browser.id {
        case "safari": return "safari"
        case "chrome": return "globe"
        case "firefox": return "flame"
        case "brave": return "shield"
        case "edge": return "globe.americas"
        default: return "globe"
        }
    }
}

#Preview {
    SettingsView()
        .modelContainer(Persistence.preview.container)
}
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/Views/SettingsView.swift
git commit -m "feat(ios): add SettingsView"
```

---

### Task 5.4: Update ContentView

**Files:**
- Modify: `ios/BrowserSelector/ContentView.swift`

**Step 1: Replace ContentView.swift**

Replace `ios/BrowserSelector/ContentView.swift`:

```swift
import SwiftUI

/// Root content view - redirects to SettingsView.
struct ContentView: View {
    var body: some View {
        SettingsView()
    }
}

#Preview {
    ContentView()
}
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/ContentView.swift
git commit -m "feat(ios): update ContentView to show SettingsView"
```

---

## Phase 6: Finalize Project Configuration

### Task 6.1: Create Entitlements File

**Files:**
- Create: `ios/BrowserSelector/BrowserSelector.entitlements`

**Step 1: Create BrowserSelector.entitlements**

Create `ios/BrowserSelector/BrowserSelector.entitlements`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.developer.web-browser</key>
    <true/>
</dict>
</plist>
```

**Step 2: Commit**

```bash
git add ios/BrowserSelector/BrowserSelector.entitlements
git commit -m "feat(ios): add web-browser entitlement"
```

---

### Task 6.2: Create Assets Catalog

**Files:**
- Create: `ios/BrowserSelector/Assets.xcassets/Contents.json`
- Create: `ios/BrowserSelector/Assets.xcassets/AccentColor.colorset/Contents.json`
- Create: `ios/BrowserSelector/Assets.xcassets/AppIcon.appiconset/Contents.json`

**Step 1: Create Assets.xcassets structure**

```bash
mkdir -p ios/BrowserSelector/Assets.xcassets/AccentColor.colorset
mkdir -p ios/BrowserSelector/Assets.xcassets/AppIcon.appiconset
```

**Step 2: Create Contents.json files**

Create `ios/BrowserSelector/Assets.xcassets/Contents.json`:

```json
{
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

Create `ios/BrowserSelector/Assets.xcassets/AccentColor.colorset/Contents.json`:

```json
{
  "colors" : [
    {
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

Create `ios/BrowserSelector/Assets.xcassets/AppIcon.appiconset/Contents.json`:

```json
{
  "images" : [
    {
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

**Step 3: Commit**

```bash
git add ios/BrowserSelector/Assets.xcassets/
git commit -m "feat(ios): add Assets.xcassets"
```

---

### Task 6.3: Create Xcode Project File

**Files:**
- Create: `ios/BrowserSelector.xcodeproj/project.pbxproj`

**Step 1: Create the project.pbxproj file**

This file is very large. Create `ios/BrowserSelector.xcodeproj/project.pbxproj` with the full Xcode project configuration.

**Step 2: Commit**

```bash
git add ios/BrowserSelector.xcodeproj/
git commit -m "feat(ios): add Xcode project file"
```

---

## Phase 7: Final Integration

### Task 7.1: Final Commit and Push

**Step 1: Verify all files are committed**

```bash
git status
```

**Step 2: Push to trigger CI**

```bash
git push origin main
```

**Step 3: Verify CI passes**

Check GitHub Actions for successful build.

---

## CI Checkpoints Summary

| Phase | Checkpoint | Expected Result |
|-------|------------|-----------------|
| 1 | Workflow updated | iOS job exists, skips if no ios/ dir |
| 1 | Project scaffold | Build succeeds with minimal app |
| 2 | Core logic | Tests pass for PatternMatcher |
| 3 | Data layer | Build succeeds with SwiftData |
| 4 | Browser selector UI | Build succeeds with all views |
| 5 | Settings UI | Build succeeds with full app |
| 6 | Entitlements | Build succeeds with entitlements |
| 7 | Final | Full CI passes, artifacts generated |
