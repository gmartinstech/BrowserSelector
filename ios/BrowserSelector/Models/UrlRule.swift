import Foundation
import SwiftData

/// Represents a URL pattern rule that maps URLs to specific browsers.
@Model
class UrlRule {
    var pattern: String
    var browserScheme: String
    var priority: Int
    var createdAt: Date

    init(pattern: String, browserScheme: String, priority: Int = 0) {
        self.pattern = pattern
        self.browserScheme = browserScheme
        self.priority = priority
        self.createdAt = Date()
    }

    /// Creates a copy with updated priority.
    func withPriority(_ priority: Int) -> UrlRule {
        UrlRule(pattern: pattern, browserScheme: browserScheme, priority: priority)
    }
}
