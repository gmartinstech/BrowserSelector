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
