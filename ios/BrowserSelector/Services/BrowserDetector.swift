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
