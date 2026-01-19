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

        // Get host and path
        let components = cleanUrl.components(separatedBy: "/")
        let host = components.first ?? cleanUrl
        let path = components.count > 1 ? "/" + components.dropFirst().joined(separator: "/") : ""

        // Apply the browser's URL format
        return urlFormat
            .replacingOccurrences(of: "{url}", with: url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? url)
            .replacingOccurrences(of: "{host}", with: host)
            .replacingOccurrences(of: "{path}", with: path)
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
