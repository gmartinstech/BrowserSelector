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
                        .textInputAutocapitalization(.never)
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
        PatternMatcher.isValidPattern(pattern)
    }

    private func saveRule() {
        repository.createRule(pattern: pattern, browserScheme: selectedBrowserScheme)
        dismiss()
    }
}

#Preview {
    AddRuleView(repository: BrowserRepository(modelContext: Persistence.preview.container.mainContext))
}
