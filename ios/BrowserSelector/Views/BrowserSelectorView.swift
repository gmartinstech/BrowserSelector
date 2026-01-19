import SwiftUI

/// Main view for selecting a browser to open a URL.
struct BrowserSelectorView: View {
    @StateObject private var viewModel: BrowserSelectorViewModel
    @Environment(\.dismiss) private var dismiss

    let url: String

    init(url: String, repository: BrowserRepository) {
        self.url = url
        _viewModel = StateObject(wrappedValue: BrowserSelectorViewModel(repository: repository))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // URL display
                urlHeader

                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxHeight: .infinity)
                } else if viewModel.browsers.isEmpty {
                    noBrowsersView
                } else {
                    browserList
                }

                // Remember checkbox and pattern
                rememberSection

                // Bottom buttons
                bottomButtons
            }
            .navigationTitle("Open with")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                viewModel.setup(with: url)
            }
            .onChange(of: viewModel.shouldDismiss) { _, shouldDismiss in
                if shouldDismiss {
                    dismiss()
                }
            }
        }
    }

    private var urlHeader: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Opening:")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(viewModel.displayUrl)
                .font(.subheadline)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
    }

    private var browserList: some View {
        List {
            ForEach(viewModel.browsers) { browser in
                BrowserRow(browser: browser) {
                    viewModel.selectBrowser(browser)
                }
            }
        }
        .listStyle(.plain)
    }

    private var noBrowsersView: some View {
        ContentUnavailableView(
            "No Browsers Found",
            systemImage: "globe",
            description: Text("No other browsers are installed on this device.")
        )
    }

    private var rememberSection: some View {
        VStack(spacing: 12) {
            Toggle(isOn: $viewModel.rememberChoice) {
                Text("Remember for this domain")
            }
            .padding(.horizontal)

            if viewModel.rememberChoice {
                TextField("Pattern", text: $viewModel.pattern)
                    .textFieldStyle(.roundedBorder)
                    .font(.footnote)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(.horizontal)
            }
        }
        .padding(.vertical)
        .background(Color(.secondarySystemBackground))
    }

    private var bottomButtons: some View {
        HStack(spacing: 12) {
            Button {
                // Open share sheet - to be implemented
            } label: {
                Label("More Options", systemImage: "square.and.arrow.up")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
        .padding()
    }
}

#Preview {
    BrowserSelectorView(
        url: "https://github.com/example/repo",
        repository: BrowserRepository(modelContext: Persistence.preview.container.mainContext)
    )
}
