import SwiftUI

/// A row displaying a browser option.
struct BrowserRow: View {
    let browser: Browser
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 16) {
                // Browser icon placeholder (system image based on browser)
                Image(systemName: iconName)
                    .font(.title)
                    .frame(width: 44, height: 44)
                    .foregroundStyle(.primary)

                VStack(alignment: .leading, spacing: 2) {
                    Text(browser.name)
                        .font(.body)
                        .foregroundStyle(.primary)

                    if browser.isDefault {
                        Text("Default")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var iconName: String {
        switch browser.id {
        case "safari": return "safari"
        case "chrome": return "globe"
        case "firefox": return "flame"
        case "brave": return "shield"
        case "edge": return "globe.americas"
        case "opera", "opera-mini": return "circle.circle"
        case "duckduckgo": return "magnifyingglass"
        default: return "globe"
        }
    }
}

#Preview {
    List {
        BrowserRow(browser: Browser.knownBrowsers[0]) {}
        BrowserRow(browser: Browser.knownBrowsers[1]) {}
        BrowserRow(browser: Browser.knownBrowsers[2]) {}
    }
}
