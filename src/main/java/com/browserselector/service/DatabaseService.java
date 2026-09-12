package com.browserselector.service;

import com.browserselector.model.Browser;
import com.browserselector.model.Setting;
import com.browserselector.model.UrlRule;
import com.browserselector.util.PatternMatcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DatabaseService {

    private static final String DB_NAME = "browser_selector.db";
    private static DatabaseService instance;
    private final String dbUrl;

    // Single persistent connection
    private Connection connection;

    // In-memory caches for frequently accessed data
    private Map<String, Browser> browserCache;
    private List<UrlRule> rulesCache;
    private boolean browserCacheValid = false;
    private boolean rulesCacheValid = false;

    private DatabaseService() {
        var appData = System.getenv("APPDATA");
        var dbPath = appData != null
            ? Paths.get(appData, "BrowserSwitch", DB_NAME)
            : Paths.get(System.getProperty("user.home"), ".browserselector", DB_NAME);

        dbPath.getParent().toFile().mkdirs();
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        this.browserCache = new ConcurrentHashMap<>();
        this.rulesCache = new ArrayList<>();
        initDatabase();

        // Register shutdown hook to close connection
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeConnection));
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(dbUrl);
            // Enable WAL mode for better concurrency
            try (var stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
            }
        }
        return connection;
    }

    private void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            // Ignore on shutdown
        }
    }

    private void initDatabase() {
        var schema = """
            CREATE TABLE IF NOT EXISTS browsers (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                exe_path TEXT NOT NULL,
                icon_path TEXT,
                profile_arg TEXT,
                incognito_arg TEXT,
                is_profile INTEGER DEFAULT 0,
                parent_browser_id TEXT,
                enabled INTEGER DEFAULT 1
            );

            CREATE TABLE IF NOT EXISTS url_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pattern TEXT UNIQUE NOT NULL,
                browser_id TEXT NOT NULL,
                priority INTEGER DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            );

            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT
            );

            INSERT OR IGNORE INTO settings (key, value) VALUES ('advanced_mode', 'false');
            INSERT OR IGNORE INTO settings (key, value) VALUES ('show_incognito', 'true');
            INSERT OR IGNORE INTO settings (key, value) VALUES ('dark_theme', 'false');
            INSERT OR IGNORE INTO settings (key, value) VALUES ('system_theme', 'true');
            """;

        try {
            var conn = getConnection();
            try (var stmt = conn.createStatement()) {
                for (var sql : schema.split(";")) {
                    if (!sql.isBlank()) {
                        stmt.execute(sql.trim());
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    // Browser operations with caching
    public List<Browser> getAllBrowsers() {
        if (browserCacheValid && !browserCache.isEmpty()) {
            return new ArrayList<>(browserCache.values());
        }

        var browsers = new ArrayList<Browser>();
        var sql = "SELECT * FROM browsers ORDER BY is_profile, name";

        try {
            var conn = getConnection();
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(sql)) {
                browserCache.clear();
                while (rs.next()) {
                    var browser = browserFromResultSet(rs);
                    browsers.add(browser);
                    browserCache.put(browser.id(), browser);
                }
                browserCacheValid = true;
            }
        } catch (SQLException e) {
            System.err.println("Error loading browsers: " + e.getMessage());
        }
        return browsers;
    }

    public List<Browser> getEnabledBrowsers() {
        return getAllBrowsers().stream()
            .filter(Browser::enabled)
            .toList();
    }

    public Optional<Browser> getBrowser(String id) {
        // Check cache first
        if (browserCacheValid && browserCache.containsKey(id)) {
            return Optional.of(browserCache.get(id));
        }

        var sql = "SELECT * FROM browsers WHERE id = ?";

        try {
            var conn = getConnection();
            try (var pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, id);
                try (var rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        var browser = browserFromResultSet(rs);
                        browserCache.put(id, browser);
                        return Optional.of(browser);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting browser: " + e.getMessage());
        }
        return Optional.empty();
    }

    public void saveBrowser(Browser browser) {
        var sql = """
            INSERT OR REPLACE INTO browsers
            (id, name, exe_path, icon_path, profile_arg, incognito_arg, is_profile, parent_browser_id, enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try {
            var conn = getConnection();
            try (var pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, browser.id());
                pstmt.setString(2, browser.name());
                pstmt.setString(3, browser.exePath().toString());
                pstmt.setString(4, browser.iconPath() != null ? browser.iconPath().toString() : null);
                pstmt.setString(5, browser.profileArg());
                pstmt.setString(6, browser.incognitoArg());
                pstmt.setInt(7, browser.isProfile() ? 1 : 0);
                pstmt.setString(8, browser.parentBrowserId());
                pstmt.setInt(9, browser.enabled() ? 1 : 0);
                pstmt.executeUpdate();

                // Update cache
                browserCache.put(browser.id(), browser);
            }
        } catch (SQLException e) {
            System.err.println("Error saving browser: " + e.getMessage());
        }
    }

    public void deleteBrowser(String id) {
        var sql = "DELETE FROM browsers WHERE id = ?";

        try {
            var conn = getConnection();
            try (var pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, id);
                pstmt.executeUpdate();

                // Update cache
                browserCache.remove(id);
            }
        } catch (SQLException e) {
            System.err.println("Error deleting browser: " + e.getMessage());
        }
    }

    public void clearBrowsers() {
        try {
            var conn = getConnection();
            try (var stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM browsers");

                // Clear cache
                browserCache.clear();
                browserCacheValid = false;
            }
        } catch (SQLException e) {
            System.err.println("Error clearing browsers: " + e.getMessage());
        }
    }

    private Browser browserFromResultSet(ResultSet rs) throws SQLException {
        var iconPath = rs.getString("icon_path");
        return new Browser(
            rs.getString("id"),
            rs.getString("name"),
            Path.of(rs.getString("exe_path")),
            iconPath != null ? Path.of(iconPath) : null,
            rs.getString("profile_arg"),
            rs.getString("incognito_arg"),
            rs.getInt("is_profile") == 1,
            rs.getString("parent_browser_id"),
            rs.getInt("enabled") == 1
        );
    }

    // URL Rule operations with caching
    public List<UrlRule> getAllRules() {
        if (rulesCacheValid && !rulesCache.isEmpty()) {
            return new ArrayList<>(rulesCache);
        }

        var rules = new ArrayList<UrlRule>();
        var sql = "SELECT * FROM url_rules ORDER BY priority DESC, id";

        try {
            var conn = getConnection();
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    rules.add(ruleFromResultSet(rs));
                }
                rulesCache = new ArrayList<>(rules);
                rulesCacheValid = true;
            }
        } catch (SQLException e) {
            System.err.println("Error loading rules: " + e.getMessage());
        }
        return rules;
    }

    public Optional<UrlRule> findMatchingRule(String url) {
        var rules = getAllRules();
        return rules.stream()
            .filter(rule -> PatternMatcher.matches(rule.pattern(), url))
            .findFirst();
    }

    public void saveRule(UrlRule rule) {
        var sql = """
            INSERT OR REPLACE INTO url_rules (id, pattern, browser_id, priority)
            VALUES (COALESCE(?, (SELECT id FROM url_rules WHERE pattern = ?)), ?, ?, ?)
            """;

        try {
            var conn = getConnection();
            try (var pstmt = conn.prepareStatement(sql)) {
                pstmt.setObject(1, rule.id() > 0 ? rule.id() : null);
                pstmt.setString(2, rule.pattern());
                pstmt.setString(3, rule.pattern());
                pstmt.setString(4, rule.browserId());
                pstmt.setInt(5, rule.priority());
                pstmt.executeUpdate();

                // Invalidate cache
                rulesCacheValid = false;
            }
        } catch (SQLException e) {
            System.err.println("Error saving rule: " + e.getMessage());
        }
    }

    public void deleteRule(int id) {
        var sql = "DELETE FROM url_rules WHERE id = ?";

        try {
            var conn = getConnection();
            try (var pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();

                // Invalidate cache
                rulesCacheValid = false;
            }
        } catch (SQLException e) {
            System.err.println("Error deleting rule: " + e.getMessage());
        }
    }

    private UrlRule ruleFromResultSet(ResultSet rs) throws SQLException {
        return new UrlRule(
            rs.getInt("id"),
            rs.getString("pattern"),
            rs.getString("browser_id"),
            rs.getInt("priority"),
            parseTimestamp(rs.getString("created_at"))
        );
    }

    /**
     * Parses the created_at column defensively. SQLite's CURRENT_TIMESTAMP stores
     * "yyyy-MM-dd HH:mm:ss" in UTC (space separator, no zone), which Instant.parse
     * rejects; older releases may also have written ISO-8601 instants. A legacy or
     * malformed row must never crash the UI, so fall back to EPOCH.
     */
    private static Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        String v = value.trim();
        try {
            if (v.indexOf('T') >= 0) {
                return Instant.parse(v);
            }
            return LocalDateTime.parse(v,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]"))
                .toInstant(ZoneOffset.UTC);
        } catch (DateTimeException e) {
            System.err.println("Unparseable created_at value '" + value + "': " + e.getMessage());
            return Instant.EPOCH;
        }
    }

    // Settings operations
    public boolean getToggle(String key, boolean defaultValue) {
        var sql = "SELECT value FROM settings WHERE key = ?";

        try {
            var conn = getConnection();
            try (var pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, key);
                try (var rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return Boolean.parseBoolean(rs.getString("value"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting setting: " + e.getMessage());
        }
        return defaultValue;
    }

    public String getText(String key, String defaultValue) {
        var sql = "SELECT value FROM settings WHERE key = ?";

        try {
            var conn = getConnection();
            try (var pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, key);
                try (var rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("value");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting setting: " + e.getMessage());
        }
        return defaultValue;
    }

    public void saveSetting(Setting setting) {
        var sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";

        try {
            var conn = getConnection();
            try (var pstmt = conn.prepareStatement(sql)) {
                switch (setting) {
                    case Setting.Toggle t -> {
                        pstmt.setString(1, t.key());
                        pstmt.setString(2, String.valueOf(t.value()));
                    }
                    case Setting.Text t -> {
                        pstmt.setString(1, t.key());
                        pstmt.setString(2, t.value());
                    }
                }
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error saving setting: " + e.getMessage());
        }
    }
}
