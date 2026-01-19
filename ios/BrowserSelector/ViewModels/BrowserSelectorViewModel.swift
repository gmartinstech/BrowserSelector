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
}
