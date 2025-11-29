package com.browserselector.service;

import com.browserselector.model.Browser;
import com.browserselector.model.Setting;
import com.browserselector.model.UrlRule;
import com.browserselector.util.PatternMatcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DatabaseService {

    private static final String DB_NAME = "browser_selector.db";
    private static DatabaseService instance;
    private final String dbUrl;

    private DatabaseService() {
        var appData = System.getenv("APPDATA");
        var dbPath = appData != null
            ? Paths.get(appData, "BrowserSelector", DB_NAME)
            : Paths.get(System.getProperty("user.home"), ".browserselector", DB_NAME);

        dbPath.getParent().toFile().mkdirs();
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        initDatabase();
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
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

        try (var conn = DriverManager.getConnection(dbUrl);
             var stmt = conn.createStatement()) {
            for (var sql : schema.split(";")) {
                if (!sql.isBlank()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    // Browser operations
    public List<Browser> getAllBrowsers() {
        var browsers = new ArrayList<Browser>();
        var sql = "SELECT * FROM browsers ORDER BY is_profile, name";

        try (var conn = DriverManager.getConnection(dbUrl);
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                browsers.add(browserFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return browsers;
    }

    public List<Browser> getEnabledBrowsers() {
        var browsers = new ArrayList<Browser>();
        var sql = "SELECT * FROM browsers WHERE enabled = 1 ORDER BY is_profile, name";

        try (var conn = DriverManager.getConnection(dbUrl);
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                browsers.add(browserFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return browsers;
    }

    public Optional<Browser> getBrowser(String id) {
        var sql = "SELECT * FROM browsers WHERE id = ?";

        try (var conn = DriverManager.getConnection(dbUrl);
             var pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(browserFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public void saveBrowser(Browser browser) {
        var sql = """
            INSERT OR REPLACE INTO browsers
            (id, name, exe_path, icon_path, profile_arg, incognito_arg, is_profile, parent_browser_id, enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (var conn = DriverManager.getConnection(dbUrl);
             var pstmt = conn.prepareStatement(sql)) {

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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteBrowser(String id) {
        var sql = "DELETE FROM browsers WHERE id = ?";

        try (var conn = DriverManager.getConnection(dbUrl);
             var pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void clearBrowsers() {
        try (var conn = DriverManager.getConnection(dbUrl);
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM browsers");
        } catch (SQLException e) {
            e.printStackTrace();
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

    // URL Rule operations
    public List<UrlRule> getAllRules() {
        var rules = new ArrayList<UrlRule>();
        var sql = "SELECT * FROM url_rules ORDER BY priority DESC, id";

        try (var conn = DriverManager.getConnection(dbUrl);
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                rules.add(ruleFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
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

        try (var conn = DriverManager.getConnection(dbUrl);
             var pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, rule.id() > 0 ? rule.id() : null);
            pstmt.setString(2, rule.pattern());
            pstmt.setString(3, rule.pattern());
            pstmt.setString(4, rule.browserId());
            pstmt.setInt(5, rule.priority());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteRule(int id) {
        var sql = "DELETE FROM url_rules WHERE id = ?";

        try (var conn = DriverManager.getConnection(dbUrl);
             var pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private UrlRule ruleFromResultSet(ResultSet rs) throws SQLException {
        return new UrlRule(
            rs.getInt("id"),
            rs.getString("pattern"),
            rs.getString("browser_id"),
            rs.getInt("priority"),
            Instant.parse(rs.getString("created_at") + "Z")
        );
    }

    // Settings operations
    public boolean getToggle(String key, boolean defaultValue) {
        var sql = "SELECT value FROM settings WHERE key = ?";

        try (var conn = DriverManager.getConnection(dbUrl);
             var pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, key);
            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Boolean.parseBoolean(rs.getString("value"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    public String getText(String key, String defaultValue) {
        var sql = "SELECT value FROM settings WHERE key = ?";

        try (var conn = DriverManager.getConnection(dbUrl);
             var pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, key);
            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    public void saveSetting(Setting setting) {
        var sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";

        try (var conn = DriverManager.getConnection(dbUrl);
             var pstmt = conn.prepareStatement(sql)) {

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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
