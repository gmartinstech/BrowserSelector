import Foundation
import SwiftUI

/// ViewModel for the settings view.
@MainActor
class SettingsViewModel: ObservableObject {

    @Published var browsers: [Browser] = []
    @Published var rules: [UrlRule] = []

    private let repository: BrowserRepository

    init(repository: BrowserRepository) {
        self.repository = repository
        refresh()
    }

    /// Refreshes browsers and rules.
    func refresh() {
        repository.refreshBrowsers()
        repository.loadRules()
        browsers = repository.browsers
        rules = repository.rules
    }

    /// Gets the browser name for a given scheme.
    func browserName(for scheme: String) -> String {
        repository.getBrowser(byScheme: scheme)?.name ?? scheme
    }

    /// Deletes rules at the given offsets.
    func deleteRules(at offsets: IndexSet) {
        repository.deleteRules(at: offsets)
        rules = repository.rules
    }
}
