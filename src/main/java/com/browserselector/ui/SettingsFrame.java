package com.browserselector.ui;

import com.browserselector.model.Browser;
import com.browserselector.model.Setting;
import com.browserselector.model.UrlRule;
import com.browserselector.service.*;
import com.browserselector.util.BrowserUtils;
import com.browserselector.util.Icons;
import com.browserselector.util.PatternMatcher;
import com.browserselector.util.WindowsTheme;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private TableRowSorter<DefaultTableModel> rulesSorter;
    private JScrollPane rulesScroll;
    private JTextField searchField;

    private JCheckBox advancedModeCheck;
    private JCheckBox showIncognitoCheck;
    private JCheckBox darkThemeCheck;
    private JCheckBox systemThemeCheck;

    private boolean advancedMode;

    private static SettingsFrame current;

    /** Whole-app single instance: focus the existing Settings window, or create the only one. */
    public static void focusOrCreate() {
        if (current != null && current.isDisplayable()) {
            current.setState(Frame.NORMAL);
            current.toFront();
            current.requestFocus();
            return;
        }
        current = new SettingsFrame();
        current.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                current = null;
            }
        });
        current.setVisible(true);
    }

    public SettingsFrame() {
        super("Browser Selector - Settings");
        this.db = DatabaseService.getInstance();
        this.registry = new RegistryService();
        this.browserDetector = new BrowserDetector();
        this.profileDetector = new ProfileDetector();
        this.advancedMode = db.getToggle(Setting.Toggle.ADVANCED_MODE, false);

        loadAppIcon();
        initUI();
        centerOnScreen();
    }

    private void loadAppIcon() {
        BrowserUtils.setAppIcon(this);
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(700, 500);

        tabbedPane = new JTabbedPane();
        rebuildTabs();

        add(tabbedPane);
    }

    /** Rebuilds the tabs in place so advanced-mode changes apply instantly. */
    private void rebuildTabs() {
        tabbedPane.removeAll();

        tabbedPane.addTab("URL Rules", createRulesPanel());

        // Browsers tab (advanced mode)
        if (advancedMode) {
            tabbedPane.addTab("Browsers", createBrowsersPanel());
        }

        // General tab
        tabbedPane.addTab("General", createSettingsPanel());

        loadData();
        tabbedPane.revalidate();
        tabbedPane.repaint();
    }

    private JPanel createRulesPanel() {
        var panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Table — column 0 is the match position; row order is precedence.
        rulesModel = new DefaultTableModel(new String[]{"#", "Pattern", "Browser"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        rulesSorter = new TableRowSorter<>(rulesModel);
        rulesTable = new JTable(rulesModel);
        rulesTable.setRowSorter(rulesSorter);
        rulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rulesTable.getColumnModel().getColumn(0).setPreferredWidth(36);
        rulesTable.getColumnModel().getColumn(0).setMaxWidth(48);

        rulesScroll = new JScrollPane(rulesTable);
        panel.add(rulesScroll, BorderLayout.CENTER);

        // Search box
        var searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        searchField = new JTextField(22);
        var searchLabel = new JLabel("Search:");
        searchLabel.setLabelFor(searchField);
        searchField.setToolTipText("Filter rules by pattern or browser");
        searchField.getDocument().addDocumentListener(docListener(this::applyRulesFilter));
        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        panel.add(searchPanel, BorderLayout.NORTH);

        // Buttons — one primary action, grouped reorder controls for power users
        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        var addBtn = new JButton("Add Rule");
        addBtn.addActionListener(e -> addRule());
        SelectorDialog.applyAccent(addBtn);

        var deleteBtn = new JButton("Delete");
        deleteBtn.addActionListener(e -> deleteSelectedRule());

        buttonPanel.add(addBtn);
        buttonPanel.add(deleteBtn);

        if (advancedMode) {
            buttonPanel.add(Box.createHorizontalStrut(12));

            var moveUpBtn = new JButton("Move Up");
            moveUpBtn.addActionListener(e -> moveRule(-1));

            var moveDownBtn = new JButton("Move Down");
            moveDownBtn.addActionListener(e -> moveRule(1));

            buttonPanel.add(moveUpBtn);
            buttonPanel.add(moveDownBtn);
        }

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
        regPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (IS_WINDOWS) {
            var registerBtn = new JButton(registry.isRegistered() ? "Re-register" : "Register as Default");
            registerBtn.addActionListener(e -> registerAsDefault());

            var openSettingsBtn = new JButton("Open Windows Settings");
            openSettingsBtn.addActionListener(e -> registry.openDefaultAppsSettings());

            var registered = registry.isRegistered();
            var statusLabel = new JLabel(registered ? "Registered" : "Not registered");
            var green = UIManager.getColor("Actions.Green");
            statusLabel.setForeground(registered
                ? (green != null ? green : new Color(0, 150, 0))
                : UIManager.getColor("Label.disabledForeground"));

            regPanel.add(registerBtn);
            regPanel.add(openSettingsBtn);
            regPanel.add(statusLabel);
        } else {
            regPanel.add(new JLabel("Linux mode - browsers detected from system"));
        }
        regPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, regPanel.getPreferredSize().height + 20));
        settingsPanel.add(regPanel);

        // Appearance section
        var appearancePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        appearancePanel.setBorder(BorderFactory.createTitledBorder("Appearance"));
        appearancePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        systemThemeCheck = new JCheckBox("Use system theme",
            db.getToggle(Setting.Toggle.SYSTEM_THEME, true));
        systemThemeCheck.addActionListener(e -> updateThemeSettings());

        darkThemeCheck = new JCheckBox("Dark theme",
            db.getToggle(Setting.Toggle.DARK_THEME, false));
        darkThemeCheck.setEnabled(!systemThemeCheck.isSelected());
        darkThemeCheck.addActionListener(e -> updateThemeSettings());

        appearancePanel.add(systemThemeCheck);
        appearancePanel.add(darkThemeCheck);
        appearancePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, appearancePanel.getPreferredSize().height + 20));
        settingsPanel.add(appearancePanel);

        // Behavior section
        var behaviorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        behaviorPanel.setBorder(BorderFactory.createTitledBorder("Behavior"));
        behaviorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        showIncognitoCheck = new JCheckBox("Show incognito option (Shift+click)",
            db.getToggle(Setting.Toggle.SHOW_INCOGNITO, true));
        showIncognitoCheck.addActionListener(e ->
            db.saveSetting(Setting.toggle(Setting.Toggle.SHOW_INCOGNITO, showIncognitoCheck.isSelected())));

        behaviorPanel.add(showIncognitoCheck);
        behaviorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, behaviorPanel.getPreferredSize().height + 20));
        settingsPanel.add(behaviorPanel);

        // Advanced section
        var advancedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        advancedPanel.setBorder(BorderFactory.createTitledBorder("Advanced"));
        advancedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        advancedModeCheck = new JCheckBox("Enable advanced mode", advancedMode);
        advancedModeCheck.addActionListener(e -> toggleAdvancedMode());

        advancedPanel.add(advancedModeCheck);
        advancedPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, advancedPanel.getPreferredSize().height + 20));
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
        var rules = db.getAllRules();
        for (int i = 0; i < rules.size(); i++) {
            var rule = rules.get(i);
            var browserName = db.getBrowser(rule.browserId())
                .map(Browser::name)
                .orElse(rule.browserId());
            rulesModel.addRow(new Object[]{i + 1, rule.pattern(), browserName});
        }
        updateRulesEmptyState();
        applyRulesFilter();
    }

    private void updateRulesEmptyState() {
        if (rulesModel.getRowCount() == 0) {
            var empty = new JPanel(new GridBagLayout());
            var label = new JLabel("<html><center>No rules yet — links will prompt until you add one.<br><br>"
                + "Add a rule to send known domains to the right browser automatically.</center></html>");
            label.setForeground(UIManager.getColor("Label.disabledForeground"));
            empty.add(label);
            rulesScroll.setViewportView(empty);
        } else {
            rulesScroll.setViewportView(rulesTable);
        }
    }

    private void applyRulesFilter() {
        var text = searchField.getText().trim();
        rulesSorter.setRowFilter(text.isEmpty() ? null
            : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text), 1, 2));
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

        // One form with live validation and a match preview, instead of two
        // blind modals.
        var patternField = new JTextField(22);
        var browserCombo = new JComboBox<>(browsers.toArray(new Browser[0]));
        browserCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof Browser b) {
                    setText(b.displayName());
                    setIcon(Icons.forBrowser(b));
                }
                return this;
            }
        });
        var preview = new JLabel(" ");
        preview.setForeground(UIManager.getColor("Label.disabledForeground"));

        Runnable update = () -> {
            var pattern = patternField.getText().trim();
            var ok = PatternMatcher.isValidPattern(pattern);
            patternField.putClientProperty("JComponent.outline",
                pattern.isEmpty() || ok ? null : "error");
            var browser = (Browser) browserCombo.getSelectedItem();
            if (ok && browser != null) {
                preview.setText("Matches e.g. " + sampleUrlFor(pattern) + "  →  " + browser.name().trim());
            } else if (!pattern.isEmpty()) {
                preview.setText("Invalid pattern — try *.example.org or github.com/*");
            } else {
                preview.setText(" ");
            }
        };
        patternField.getDocument().addDocumentListener(docListener(update));
        browserCombo.addActionListener(e -> update.run());
        update.run();

        var form = new JPanel(new GridLayout(0, 1, 8, 8));

        var patternRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        var patternLabel = new JLabel("URL pattern:");
        patternLabel.setLabelFor(patternField);
        patternRow.add(patternLabel);
        patternRow.add(patternField);
        form.add(patternRow);

        var browserRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        var browserLabel = new JLabel("Open with:");
        browserLabel.setLabelFor(browserCombo);
        browserRow.add(browserLabel);
        browserRow.add(browserCombo);
        form.add(browserRow);

        var previewRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        previewRow.add(preview);
        form.add(previewRow);

        var result = JOptionPane.showConfirmDialog(this, form, "Add Rule",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        var pattern = patternField.getText().trim();
        if (!PatternMatcher.isValidPattern(pattern)) {
            JOptionPane.showMessageDialog(this,
                "“" + pattern + "” is not a valid URL pattern. Use forms like *.example.org or github.com/*.",
                "Invalid Pattern",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        var browser = (Browser) browserCombo.getSelectedItem();
        if (browser == null) return;

        var existing = db.getAllRules().stream()
            .filter(r -> r.pattern().equalsIgnoreCase(pattern))
            .findFirst();
        if (existing.isPresent() && existing.get().browserId().equals(browser.id())) {
            JOptionPane.showMessageDialog(this,
                "'" + pattern + "' already opens in " + browser.name().trim() + ".",
                "Rule Exists",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (existing.isPresent()) {
            var confirm = JOptionPane.showConfirmDialog(this,
                "A rule for '" + pattern + "' already exists. Replace it?",
                "Replace Rule",
                JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        db.saveRule(new UrlRule(pattern, browser.id()));
        loadRules();
    }

    private void deleteSelectedRule() {
        int viewRow = rulesTable.getSelectedRow();
        if (viewRow < 0) return;
        int row = rulesTable.convertRowIndexToModel(viewRow);

        var rules = db.getAllRules();
        if (row < 0 || row >= rules.size()) return;
        var rule = rules.get(row);

        var confirm = JOptionPane.showConfirmDialog(this,
            "Delete rule '" + rule.pattern() + "'?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            db.deleteRule(rule.id());
            loadRules();
        }
    }

    private void moveRule(int direction) {
        int viewRow = rulesTable.getSelectedRow();
        if (viewRow < 0) return;
        int row = rulesTable.convertRowIndexToModel(viewRow);

        var rules = db.getAllRules();
        var newRow = row + direction;
        if (row < 0 || row >= rules.size() || newRow < 0 || newRow >= rules.size()) return;

        // Row order is precedence: reorder, then renumber so the persisted
        // priority matches exactly what the table shows.
        var reordered = new ArrayList<>(rules);
        var moved = reordered.remove(row);
        reordered.add(newRow, moved);

        int n = reordered.size();
        for (int i = 0; i < n; i++) {
            var rule = reordered.get(i);
            int newPriority = n - i;
            if (rule.priority() != newPriority) {
                db.saveRule(rule.withPriority(newPriority));
            }
        }

        loadRules();
        int newViewRow = rulesTable.convertRowIndexToView(newRow);
        if (newViewRow >= 0) {
            rulesTable.setRowSelectionInterval(newViewRow, newViewRow);
        }
    }

    private void rescanBrowsers() {
        if (!IS_WINDOWS) {
            JOptionPane.showMessageDialog(this,
                "Browser scanning is only available on Windows",
                "Not Available",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Run browser detection in background to avoid blocking UI
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<List<Browser>, Void>() {
            @Override
            protected List<Browser> doInBackground() {
                return browserDetector.detectBrowsers();
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    var browsers = get();
                    db.clearBrowsers();
                    for (var browser : browsers) {
                        db.saveBrowser(browser);
                    }
                    if (advancedMode) {
                        loadBrowsers();
                    }
                    JOptionPane.showMessageDialog(SettingsFrame.this,
                        "Found " + browsers.size() + " browser(s)",
                        "Scan Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SettingsFrame.this,
                        "Error scanning browsers: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void detectProfiles() {
        var browsers = db.getAllBrowsers().stream()
            .filter(b -> !b.isProfile())
            .toList();

        int profileCount = 0;
        int prunedCount = 0;
        for (var browser : browsers) {
            var profiles = profileDetector.detectProfiles(browser);
            for (var profile : profiles) {
                db.saveBrowser(profile);
                profileCount++;
            }

            // Remove saved profiles that no longer exist on disk
            var validIds = profiles.stream()
                .map(Browser::id)
                .collect(java.util.stream.Collectors.toSet());
            for (var saved : db.getAllBrowsers()) {
                if (saved.isProfile() && browser.id().equals(saved.parentBrowserId())
                        && !validIds.contains(saved.id())) {
                    db.deleteBrowser(saved.id());
                    prunedCount++;
                }
            }
        }

        loadBrowsers();
        var message = "Found " + profileCount + " profile(s)"
            + (prunedCount > 0 ? "\nRemoved " + prunedCount + " stale profile(s)" : "");
        JOptionPane.showMessageDialog(this,
            message,
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
            var incognitoArg = BrowserUtils.detectIncognitoArg(name);

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
        // So the exe is at: AppName/BrowserSelector.exe (parent of runtime)
        var runtimeParent = javaHome.getParent();
        if (runtimeParent != null) {
            var jpackageExe = runtimeParent.resolve("BrowserSelector.exe");
            if (jpackageExe.toFile().exists()) {
                return jpackageExe;
            }
            var legacyExe = runtimeParent.resolve("BrowserSwitch.exe"); // pre-1.8 app images
            if (legacyExe.toFile().exists()) {
                return legacyExe;
            }
        }

        // Try current working directory
        var userDir = System.getProperty("user.dir");
        var exePath = Path.of(userDir, "BrowserSelector.exe");
        if (exePath.toFile().exists()) {
            return exePath;
        }
        var legacyLocal = Path.of(userDir, "BrowserSwitch.exe"); // pre-1.8 app images
        if (legacyLocal.toFile().exists()) {
            return legacyLocal;
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
        rebuildTabs();
    }

    private void updateThemeSettings() {
        var useSystem = systemThemeCheck.isSelected();
        darkThemeCheck.setEnabled(!useSystem);

        db.saveSetting(Setting.toggle(Setting.Toggle.SYSTEM_THEME, useSystem));
        db.saveSetting(Setting.toggle(Setting.Toggle.DARK_THEME, darkThemeCheck.isSelected()));

        // Apply theme
        try {
            if (useSystem) {
                var isDark = WindowsTheme.isSystemDark();
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

    private static javax.swing.event.DocumentListener docListener(Runnable r) {
        return new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        };
    }

    private static String sampleUrlFor(String pattern) {
        var s = "https://" + (pattern.startsWith("*.") ? pattern.substring(2) : pattern);
        return s.replace("/*", "/some-page").replace("*", "thing").replace("?", "x");
    }
}
