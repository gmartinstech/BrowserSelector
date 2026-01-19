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
