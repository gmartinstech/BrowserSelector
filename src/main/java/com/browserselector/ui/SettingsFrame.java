package com.browserselector.ui;

import com.browserselector.model.Browser;
import com.browserselector.model.Setting;
import com.browserselector.model.UrlRule;
import com.browserselector.service.*;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public class SettingsFrame extends JFrame {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private final DatabaseService db;
    private final RegistryService registry;
    private final BrowserDetector browserDetector;
    private final ProfileDetector profileDetector;

    private JTabbedPane tabbedPane;
    private JTable rulesTable;
    private JTable browsersTable;
    private DefaultTableModel rulesModel;
    private DefaultTableModel browsersModel;

    private JCheckBox advancedModeCheck;
    private JCheckBox showIncognitoCheck;
    private JCheckBox darkThemeCheck;
    private JCheckBox systemThemeCheck;

    private boolean advancedMode;

    public SettingsFrame() {
        super("Browser Switch - Settings");
        this.db = DatabaseService.getInstance();
        this.registry = new RegistryService();
        this.browserDetector = new BrowserDetector();
        this.profileDetector = new ProfileDetector();
        this.advancedMode = db.getToggle(Setting.Toggle.ADVANCED_MODE, false);

        loadAppIcon();
        initUI();
        loadData();
        centerOnScreen();
    }

    private void loadAppIcon() {
        try {
            var iconUrl = SettingsFrame.class.getResource("/icon.png");
            if (iconUrl != null) {
                var icon = new ImageIcon(iconUrl).getImage();
                setIconImage(icon);
            }
        } catch (Exception e) {
            // Icon loading failed, continue without custom icon
        }
    }

    private void initUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(700, 500);

        tabbedPane = new JTabbedPane();

        // Rules tab
        tabbedPane.addTab("URL Rules", createRulesPanel());

        // Browsers tab (advanced mode)
        if (advancedMode) {
            tabbedPane.addTab("Browsers", createBrowsersPanel());
        }

        // Settings tab
        tabbedPane.addTab("Settings", createSettingsPanel());

        add(tabbedPane);
    }

    private JPanel createRulesPanel() {
        var panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Table
        rulesModel = new DefaultTableModel(new String[]{"Pattern", "Browser", "Priority"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        rulesTable = new JTable(rulesModel);
        rulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rulesTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        rulesTable.getColumnModel().getColumn(2).setMaxWidth(80);

        panel.add(new JScrollPane(rulesTable), BorderLayout.CENTER);

        // Buttons
        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        var addBtn = new JButton("Add Rule");
        addBtn.addActionListener(e -> addRule());

        var deleteBtn = new JButton("Delete");
        deleteBtn.addActionListener(e -> deleteSelectedRule());

        if (advancedMode) {
            var moveUpBtn = new JButton("Move Up");
            moveUpBtn.addActionListener(e -> moveRule(-1));

            var moveDownBtn = new JButton("Move Down");
            moveDownBtn.addActionListener(e -> moveRule(1));

            buttonPanel.add(moveUpBtn);
            buttonPanel.add(moveDownBtn);
        }

        buttonPanel.add(addBtn);
        buttonPanel.add(deleteBtn);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBrowsersPanel() {
        var panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Table
        browsersModel = new DefaultTableModel(new String[]{"Enabled", "Name", "Path"}, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        browsersTable = new JTable(browsersModel);
        browsersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        browsersTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        browsersTable.getColumnModel().getColumn(0).setMaxWidth(80);

        browsersTable.getModel().addTableModelListener(e -> {
            if (e.getColumn() == 0) {
                int row = e.getFirstRow();
                var enabled = (Boolean) browsersModel.getValueAt(row, 0);
                var browserId = getBrowserIdAtRow(row);
                if (browserId != null) {
                    db.getBrowser(browserId).ifPresent(browser ->
                        db.saveBrowser(browser.withEnabled(enabled)));
                }
            }
        });

        panel.add(new JScrollPane(browsersTable), BorderLayout.CENTER);

        // Buttons
        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        var addBrowserBtn = new JButton("Add Browser");
        addBrowserBtn.addActionListener(e -> addBrowserManually());

        var rescanBtn = new JButton("Re-scan Browsers");
        rescanBtn.addActionListener(e -> rescanBrowsers());

        var detectProfilesBtn = new JButton("Detect Profiles");
        detectProfilesBtn.addActionListener(e -> detectProfiles());

        var deleteBtn = new JButton("Delete");
        deleteBtn.addActionListener(e -> deleteSelectedBrowser());

        buttonPanel.add(addBrowserBtn);
        buttonPanel.add(rescanBtn);
        buttonPanel.add(detectProfilesBtn);
        buttonPanel.add(deleteBtn);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSettingsPanel() {
        var panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        var settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));

        // Registration section (Windows only)
        var regPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        regPanel.setBorder(BorderFactory.createTitledBorder("Default Browser"));

        if (IS_WINDOWS) {
            var registerBtn = new JButton(registry.isRegistered() ? "Re-register" : "Register as Default");
            registerBtn.addActionListener(e -> registerAsDefault());

            var openSettingsBtn = new JButton("Open Windows Settings");
            openSettingsBtn.addActionListener(e -> registry.openDefaultAppsSettings());

            var statusLabel = new JLabel(registry.isRegistered() ? "Registered" : "Not registered");
            statusLabel.setForeground(registry.isRegistered() ? new Color(0, 150, 0) : Color.GRAY);

            regPanel.add(registerBtn);
            regPanel.add(openSettingsBtn);
            regPanel.add(statusLabel);
        } else {
            regPanel.add(new JLabel("Windows registration not available (demo mode)"));
        }
        settingsPanel.add(regPanel);

        // Appearance section
        var appearancePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        appearancePanel.setBorder(BorderFactory.createTitledBorder("Appearance"));

        systemThemeCheck = new JCheckBox("Use system theme",
            db.getToggle(Setting.Toggle.SYSTEM_THEME, true));
        systemThemeCheck.addActionListener(e -> updateThemeSettings());

        darkThemeCheck = new JCheckBox("Dark theme",
            db.getToggle(Setting.Toggle.DARK_THEME, false));
        darkThemeCheck.setEnabled(!systemThemeCheck.isSelected());
        darkThemeCheck.addActionListener(e -> updateThemeSettings());

        appearancePanel.add(systemThemeCheck);
        appearancePanel.add(darkThemeCheck);
        settingsPanel.add(appearancePanel);

        // Behavior section
        var behaviorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        behaviorPanel.setBorder(BorderFactory.createTitledBorder("Behavior"));

        showIncognitoCheck = new JCheckBox("Show incognito option (Shift+click)",
            db.getToggle(Setting.Toggle.SHOW_INCOGNITO, true));
        showIncognitoCheck.addActionListener(e ->
            db.saveSetting(Setting.toggle(Setting.Toggle.SHOW_INCOGNITO, showIncognitoCheck.isSelected())));

        behaviorPanel.add(showIncognitoCheck);
        settingsPanel.add(behaviorPanel);

        // Advanced section
        var advancedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        advancedPanel.setBorder(BorderFactory.createTitledBorder("Advanced"));

        advancedModeCheck = new JCheckBox("Enable advanced mode", advancedMode);
        advancedModeCheck.addActionListener(e -> toggleAdvancedMode());

        advancedPanel.add(advancedModeCheck);
        settingsPanel.add(advancedPanel);

        panel.add(settingsPanel, BorderLayout.NORTH);

        return panel;
    }

    private void loadData() {
        loadRules();
        if (advancedMode) {
            loadBrowsers();
        }
    }

    private void loadRules() {
        rulesModel.setRowCount(0);
        for (var rule : db.getAllRules()) {
            var browserName = db.getBrowser(rule.browserId())
                .map(Browser::name)
                .orElse(rule.browserId());
            rulesModel.addRow(new Object[]{rule.pattern(), browserName, rule.priority()});
        }
    }

    private void loadBrowsers() {
        browsersModel.setRowCount(0);
        for (var browser : db.getAllBrowsers()) {
            browsersModel.addRow(new Object[]{
                browser.enabled(),
                browser.displayName(),
                browser.exePath().toString()
            });
        }
    }

    private void addRule() {
        var browsers = db.getEnabledBrowsers();
        if (browsers.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No browsers detected. Please scan for browsers first.",
                "No Browsers",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        var pattern = JOptionPane.showInputDialog(this,
            "Enter URL pattern (e.g., *.google.com, github.com/*):",
            "Add Rule",
            JOptionPane.PLAIN_MESSAGE);

        if (pattern == null || pattern.isBlank()) return;

        var browserNames = browsers.stream().map(Browser::name).toArray(String[]::new);
        var selected = (String) JOptionPane.showInputDialog(this,
            "Select browser:",
            "Add Rule",
            JOptionPane.PLAIN_MESSAGE,
            null,
            browserNames,
            browserNames[0]);

        if (selected == null) return;

        var browser = browsers.stream()
            .filter(b -> b.name().equals(selected))
            .findFirst()
            .orElse(null);

        if (browser != null) {
            db.saveRule(new UrlRule(pattern, browser.id()));
            loadRules();
        }
    }

    private void deleteSelectedRule() {
        var row = rulesTable.getSelectedRow();
        if (row < 0) return;

        var rules = db.getAllRules();
        if (row < rules.size()) {
            db.deleteRule(rules.get(row).id());
            loadRules();
        }
    }

    private void moveRule(int direction) {
        var row = rulesTable.getSelectedRow();
        if (row < 0) return;

        var rules = db.getAllRules();
        var newRow = row + direction;
        if (newRow < 0 || newRow >= rules.size()) return;

        // Swap priorities
        var rule1 = rules.get(row);
        var rule2 = rules.get(newRow);

        db.saveRule(rule1.withPriority(rule2.priority()));
        db.saveRule(rule2.withPriority(rule1.priority()));

        loadRules();
        rulesTable.setRowSelectionInterval(newRow, newRow);
    }

    private void rescanBrowsers() {
        if (!IS_WINDOWS) {
            JOptionPane.showMessageDialog(this,
                "Browser scanning is only available on Windows",
                "Not Available",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        db.clearBrowsers();
        var browsers = browserDetector.detectBrowsers();
        for (var browser : browsers) {
            db.saveBrowser(browser);
        }
        if (advancedMode) {
            loadBrowsers();
        }
        JOptionPane.showMessageDialog(this,
            "Found " + browsers.size() + " browser(s)",
            "Scan Complete",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void detectProfiles() {
        var browsers = db.getAllBrowsers().stream()
            .filter(b -> !b.isProfile())
            .toList();

        int profileCount = 0;
        for (var browser : browsers) {
            var profiles = profileDetector.detectProfiles(browser);
            for (var profile : profiles) {
                db.saveBrowser(profile);
                profileCount++;
            }
        }

        loadBrowsers();
        JOptionPane.showMessageDialog(this,
            "Found " + profileCount + " profile(s)",
            "Profile Detection Complete",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void addBrowserManually() {
        // Create a panel for the dialog
        var panel = new JPanel(new GridLayout(0, 1, 5, 5));

        var nameField = new JTextField(20);
        var pathField = new JTextField(30);
        var browseBtn = new JButton("Browse...");

        var namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        namePanel.add(new JLabel("Browser Name:"));
        namePanel.add(nameField);

        var pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pathPanel.add(new JLabel("Executable Path:"));
        pathPanel.add(pathField);
        pathPanel.add(browseBtn);

        browseBtn.addActionListener(e -> {
            var fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Browser Executable");
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Executable Files (*.exe)", "exe"));

            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
                // Auto-fill name if empty
                if (nameField.getText().isBlank()) {
                    var fileName = fileChooser.getSelectedFile().getName();
                    var name = fileName.replace(".exe", "");
                    // Capitalize first letter
                    if (!name.isEmpty()) {
                        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    }
                    nameField.setText(name);
                }
            }
        });

        panel.add(namePanel);
        panel.add(pathPanel);

        var result = JOptionPane.showConfirmDialog(this, panel, "Add Browser",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            var name = nameField.getText().trim();
            var pathStr = pathField.getText().trim();

            if (name.isEmpty() || pathStr.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Please provide both name and path.",
                    "Invalid Input",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            var path = Path.of(pathStr);
            if (!path.toFile().exists()) {
                JOptionPane.showMessageDialog(this,
                    "The specified file does not exist.",
                    "File Not Found",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Generate ID from name
            var id = name.toLowerCase().replaceAll("[^a-z0-9]", "-");

            // Detect incognito argument based on name
            String incognitoArg = "--incognito";
            var lowerName = name.toLowerCase();
            if (lowerName.contains("firefox")) {
                incognitoArg = "-private-window";
            } else if (lowerName.contains("opera")) {
                incognitoArg = "--private";
            }

            var browser = new Browser(id, name, path, path, null, incognitoArg, false, null, true);
            db.saveBrowser(browser);
            loadBrowsers();

            JOptionPane.showMessageDialog(this,
                "Browser '" + name + "' added successfully.",
                "Browser Added",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void deleteSelectedBrowser() {
        var row = browsersTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                "Please select a browser to delete.",
                "No Selection",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        var browserId = getBrowserIdAtRow(row);
        if (browserId != null) {
            var browser = db.getBrowser(browserId);
            var name = browser.map(Browser::name).orElse(browserId);

            var confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete '" + name + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                db.deleteBrowser(browserId);
                loadBrowsers();
            }
        }
    }

    private void registerAsDefault() {
        var exePath = getExecutablePath();
        registry.registerAsUrlHandler(exePath);

        JOptionPane.showMessageDialog(this,
            "Registered! Now open Windows Settings and set Browser Selector as default for HTTP/HTTPS.",
            "Registration Complete",
            JOptionPane.INFORMATION_MESSAGE);

        registry.openDefaultAppsSettings();
    }

    private Path getExecutablePath() {
        var javaHome = Path.of(System.getProperty("java.home"));

        // For jpackage apps, java.home is inside: AppName/runtime
        // So the exe is at: AppName/BrowserSwitch.exe (parent of runtime)
        var runtimeParent = javaHome.getParent();
        if (runtimeParent != null) {
            var jpackageExe = runtimeParent.resolve("BrowserSwitch.exe");
            if (jpackageExe.toFile().exists()) {
                return jpackageExe;
            }
        }

        // Try current working directory
        var userDir = System.getProperty("user.dir");
        var exePath = Path.of(userDir, "BrowserSwitch.exe");
        if (exePath.toFile().exists()) {
            return exePath;
        }

        // Fallback to java executable with jar
        var classPath = System.getProperty("java.class.path");
        if (classPath.endsWith(".jar")) {
            return Path.of(javaHome.toString(), "bin", "javaw.exe");
        }

        return Path.of(javaHome.toString(), "bin", "java.exe");
    }

    private void toggleAdvancedMode() {
        advancedMode = advancedModeCheck.isSelected();
        db.saveSetting(Setting.toggle(Setting.Toggle.ADVANCED_MODE, advancedMode));

        JOptionPane.showMessageDialog(this,
            "Please restart the application to apply changes.",
            "Restart Required",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateThemeSettings() {
        var useSystem = systemThemeCheck.isSelected();
        darkThemeCheck.setEnabled(!useSystem);

        db.saveSetting(Setting.toggle(Setting.Toggle.SYSTEM_THEME, useSystem));
        db.saveSetting(Setting.toggle(Setting.Toggle.DARK_THEME, darkThemeCheck.isSelected()));

        // Apply theme
        try {
            if (useSystem) {
                // System theme detection is platform-specific
                var isDark = UIManager.getSystemLookAndFeelClassName().toLowerCase().contains("dark");
                if (isDark) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
            } else if (darkThemeCheck.isSelected()) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getBrowserIdAtRow(int row) {
        var browsers = db.getAllBrowsers();
        if (row >= 0 && row < browsers.size()) {
            return browsers.get(row).id();
        }
        return null;
    }

    private void centerOnScreen() {
        setLocationRelativeTo(null);
    }
}
