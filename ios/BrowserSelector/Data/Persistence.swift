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
        let schema = Schema([UrlRule.self])
        let configuration = ModelConfiguration(
            schema: schema,
            isStoredInMemoryOnly: true
        )

        do {
            let container = try ModelContainer(for: schema, configurations: [configuration])
            let context = container.mainContext

            // Add sample data
            let rule1 = UrlRule(pattern: "github.com", browserScheme: "googlechromes", priority: 1)
            let rule2 = UrlRule(pattern: "*.google.com", browserScheme: "", priority: 2)
            context.insert(rule1)
            context.insert(rule2)

            return Persistence(container: container)
        } catch {
            fatalError("Failed to create preview container: \(error)")
        }
    }()

    private init(container: ModelContainer) {
        self.container = container
    }
}
