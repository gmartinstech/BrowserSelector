import SwiftUI
import SwiftData

/// Settings view for managing browsers and rules.
struct SettingsView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var repository: BrowserRepository?
    @State private var showingAddRule = false

    var body: some View {
        NavigationStack {
            Group {
                if let repository = repository {
                    settingsContent(repository: repository)
                } else {
                    ProgressView()
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
                if let repository = repository {
                    AddRuleView(repository: repository)
                }
            }
            .onAppear {
                if repository == nil {
                    repository = BrowserRepository(modelContext: modelContext)
                }
            }
            .refreshable {
                repository?.refreshBrowsers()
                repository?.loadRules()
            }
        }
    }

    @ViewBuilder
    private func settingsContent(repository: BrowserRepository) -> some View {
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
                instructionStep(2, "Tap 'Apps' then 'Browser Selector'")
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
