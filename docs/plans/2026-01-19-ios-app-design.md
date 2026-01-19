# iOS BrowserSelector App Design

**Date:** 2026-01-19
**Status:** Approved

## Overview

Native Swift/SwiftUI iOS app that functions as a browser selector, allowing users to choose which browser opens when tapping links. Registers as the system default browser to intercept all URL opens.

## User Flow

1. User installs BrowserSelector from App Store
2. User sets as default browser: **Settings → BrowserSelector → Default Browser App → select BrowserSelector**
3. User taps any link anywhere on iOS → BrowserSelector opens
4. BrowserSelector shows:
   - URL being opened (truncated)
   - List of available browsers with icons
   - "Remember for this domain" checkbox
   - Pattern text field (when checkbox checked)
   - Cancel button
5. User taps browser → URL opens in selected browser
6. If "Remember" checked → rule saved for future auto-open

## Architecture

### Technology Stack

- **Language:** Swift 5.9+
- **UI Framework:** SwiftUI (MVVM pattern)
- **Persistence:** SwiftData
- **Minimum iOS:** 17.0
- **Target:** iPhone and iPad

### Project Structure

```
ios/
├── BrowserSelector.xcodeproj
├── BrowserSelector/
│   ├── BrowserSelectorApp.swift        # App entry, URL handling
│   ├── Entitlements.plist              # web-browser entitlement
│   ├── Info.plist                      # URL schemes config
│   │
│   ├── Models/
│   │   ├── Browser.swift               # Installed browser model
│   │   └── UrlRule.swift               # URL pattern rule (SwiftData)
│   │
│   ├── Services/
│   │   ├── BrowserDetector.swift       # Detect installed browsers
│   │   └── PatternMatcher.swift        # URL pattern matching
│   │
│   ├── Data/
│   │   ├── BrowserRepository.swift     # Data access layer
│   │   └── Persistence.swift           # SwiftData container
│   │
│   ├── ViewModels/
│   │   ├── BrowserSelectorViewModel.swift
│   │   └── SettingsViewModel.swift
│   │
│   ├── Views/
│   │   ├── BrowserSelectorView.swift   # Main browser picker
│   │   ├── SettingsView.swift          # Rules & browser management
│   │   ├── AddRuleView.swift           # Add/edit rule
│   │   └── Components/
│   │       ├── BrowserRow.swift
│   │       └── UrlRuleRow.swift
│   │
│   └── Resources/
│       └── Assets.xcassets
│
└── BrowserSelectorTests/
    ├── PatternMatcherTests.swift
    └── BrowserRepositoryTests.swift
```

## Data Models

### Browser

```swift
struct Browser: Identifiable, Hashable {
    let id: String              // URL scheme (e.g., "googlechromes")
    let name: String            // Display name (e.g., "Chrome")
    let urlScheme: String       // Scheme to open URLs
    let urlFormat: String       // Format string (e.g., "googlechromes://{url}")
    var enabled: Bool
    var isDefault: Bool
    var isInstalled: Bool       // Detected via canOpenURL
}
```

### UrlRule (SwiftData)

```swift
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
}
```

## Browser Detection

### Known iOS Browser Schemes

| Browser | Scheme | URL Format |
|---------|--------|------------|
| Safari | (system) | Direct https:// |
| Chrome | `googlechromes` | `googlechromes://{host}{path}` |
| Chrome (callback) | `googlechrome-x-callback` | `googlechrome-x-callback://x-callback-url/open/?url={url}` |
| Firefox | `firefox` | `firefox://open-url?url={url}` |
| Firefox Focus | `firefox-focus` | `firefox-focus://open-url?url={url}` |
| Brave | `brave` | `brave://open-url?url={url}` |
| Edge | `microsoft-edge` | `microsoft-edge://open?url={url}` |
| Opera Mini | `opera-https` | `opera-https://{host}{path}` |
| Opera | `touch-https` | `touch-https://{host}{path}` |
| DuckDuckGo | `ddgQuickLink` | `ddgQuickLink://{url}` |
| Yandex | `yandexbrowser-open-url` | `yandexbrowser-open-url://{url}` |
| UC Browser | `ucbrowser` | `ucbrowser://{url}` |
| Aloha | `alohabrowser` | `alohabrowser://{url}` |
| Onion Browser | `onionhttps` | `onionhttps://{host}{path}` |
| Puffin | `puffin` | `puffin://{url}` |

### Info.plist - LSApplicationQueriesSchemes

```xml
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
```

## Default Browser Entitlement

### Requirements

1. **Entitlement:** `com.apple.developer.web-browser` (managed entitlement)
2. **Request:** Email `default-app-requests@apple.com` to obtain
3. **URL Handling:** Must handle `http://` and `https://` schemes
4. **On Launch:** Must show URL field, search, or bookmarks

### Entitlements.plist

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

### Info.plist - URL Types

```xml
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
```

## GitHub Actions CI

### build-ios Job

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
          CODE_SIGNING_ALLOWED='NO'

    - name: Archive for Release
      if: steps.check_ios.outputs.IOS_EXISTS == 'true' && (startsWith(github.ref, 'refs/tags/v') || github.event_name == 'workflow_dispatch')
      working-directory: ios
      run: |
        xcodebuild archive \
          -project BrowserSelector.xcodeproj \
          -scheme BrowserSelector \
          -archivePath build/BrowserSelector.xcarchive \
          -destination 'generic/platform=iOS' \
          CODE_SIGNING_ALLOWED='NO'

    - name: Upload iOS artifact
      if: steps.check_ios.outputs.IOS_EXISTS == 'true'
      uses: actions/upload-artifact@v4
      with:
        name: browser-selector-ios
        path: ios/build/BrowserSelector.xcarchive
        if-no-files-found: warn
```

## Implementation Plan (TDD via CI)

### Phase 1: Project Scaffold
1. Create Xcode project with correct bundle ID
2. Add `build-ios` job to GitHub workflow
3. Add minimal SwiftUI app
4. **CI checkpoint:** Workflow builds ✓

### Phase 2: Core Logic
5. Port `PatternMatcher.swift` from Android
6. Add `PatternMatcherTests.swift`
7. Port `Browser.swift` and `UrlRule.swift` models
8. **CI checkpoint:** Tests pass ✓

### Phase 3: Data Layer
9. Add SwiftData persistence
10. Add `BrowserRepository.swift`
11. Add `BrowserDetector.swift`
12. **CI checkpoint:** Build + tests ✓

### Phase 4: Browser Selector UI
13. Create `BrowserSelectorView.swift`
14. Create `BrowserSelectorViewModel.swift`
15. Create `BrowserRow.swift`
16. Wire URL handling in app
17. **CI checkpoint:** Build passes ✓

### Phase 5: Settings UI
18. Create `SettingsView.swift`
19. Create `AddRuleView.swift`
20. Create `UrlRuleRow.swift`
21. **CI checkpoint:** Build passes ✓

### Phase 6: Default Browser
22. Add `Entitlements.plist`
23. Configure `Info.plist` URL schemes
24. Handle URL opens
25. **CI checkpoint:** Full build ✓

### Phase 7: Release
26. Add app icons
27. Update workflow for artifacts
28. Test release workflow
29. **CI checkpoint:** Artifacts generated ✓

## References

- [Apple: Preparing your app to be the default browser](https://developer.apple.com/documentation/xcode/preparing-your-app-to-be-the-default-browser)
- [Apple: com.apple.developer.web-browser entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.web-browser)
- [iOS URL Schemes (GitHub)](https://gist.github.com/felquis/a08ee196747f71689dcb)
- [Chrome iOS URL Schemes](https://chromium.googlesource.com/chromium/src/+/lkgr/docs/ios/opening_links.md)
- [Modern iOS Architecture 2025](https://medium.com/@csmax/the-ultimate-guide-to-modern-ios-architecture-in-2025-9f0d5fdc892f)
