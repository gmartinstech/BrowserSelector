import SwiftUI

/// A row displaying a URL rule.
struct UrlRuleRow: View {
    let rule: UrlRule
    let browserName: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(rule.pattern)
                .font(.body)

            HStack {
                Image(systemName: "arrow.right")
                    .font(.caption2)
                Text(browserName)
                    .font(.caption)
            }
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}

#Preview {
    List {
        UrlRuleRow(rule: UrlRule(pattern: "github.com", browserScheme: "googlechromes"), browserName: "Chrome")
        UrlRuleRow(rule: UrlRule(pattern: "*.google.com", browserScheme: ""), browserName: "Safari")
    }
}
