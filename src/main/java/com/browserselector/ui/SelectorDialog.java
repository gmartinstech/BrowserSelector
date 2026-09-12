package com.browserselector.ui;

import com.browserselector.model.Browser;
import com.browserselector.model.Setting;
import com.browserselector.model.UrlRule;
import com.browserselector.service.DatabaseService;
import com.browserselector.util.BrowserUtils;
import com.browserselector.util.Icons;
import com.browserselector.util.PatternMatcher;
import com.browserselector.util.UrlUtils;
import com.browserselector.util.WindowsTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;

public class SelectorDialog extends JDialog {

    private final String url;
    private final String domain;
    private final DatabaseService db;
    private final List<Browser> browsers;
    private final JFrame ownerFrame;
    private final boolean showIncognito;

    private JList<Browser> browserList;
    private JCheckBox rememberCheckbox;
    private JTextField patternField;
    private boolean shiftPressed = false;

    public SelectorDialog(String url) {
        super(createOwnerFrame(), "Select Browser", true);
        this.ownerFrame = (JFrame) getOwner();
        this.url = url;
        this.domain = UrlUtils.extractDomain(url);
        this.db = DatabaseService.getInstance();
        this.browsers = db.getEnabledBrowsers();
        this.showIncognito = db.getToggle(Setting.Toggle.SHOW_INCOGNITO, true);

        initUI();
        setupKeyBindings();
        centerOnScreen();

        // Dispose owner frame when dialog closes
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (ownerFrame != null) {
                    ownerFrame.dispose();
                }
            }
        });
    }

    /**
     * Creates a hidden owner frame for the dialog.
     * This is necessary on Windows when the app is launched as a URL handler
     * without any visible window - a modal dialog with no parent won't show properly.
     */
    private static JFrame createOwnerFrame() {
        JFrame frame = new JFrame("Browser Selector");
        frame.setUndecorated(true);
        frame.setSize(0, 0);
        frame.setLocationRelativeTo(null);
        // Make the frame appear in taskbar so dialog can show
        frame.setType(Window.Type.NORMAL);
        // Set app icon
        BrowserUtils.setAppIcon(frame);
        frame.setVisible(true);
        return frame;
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        var panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 12, 15));

        // URL display — keep the head (scheme + domain) and the tail (where
        // the specific target lives); elide the middle. Full URL on tooltip.
        var urlLabel = new JLabel(middleTruncate(url, 64));
        urlLabel.setFont(urlLabel.getFont().deriveFont(Font.PLAIN, 11f));
        urlLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        urlLabel.setToolTipText(url);
        panel.add(urlLabel, BorderLayout.NORTH);

        // Browser list
        browserList = new JList<>(browsers.toArray(new Browser[0]));
        browserList.setCellRenderer(new BrowserListRenderer());
        browserList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        browserList.setVisibleRowCount(Math.min(browsers.size(), 8));
        browserList.setSelectedIndex(0);

        browserList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    launchSelected();
                }
            }
        });

        var scrollPane = new JScrollPane(browserList);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
        panel.add(scrollPane, BorderLayout.CENTER);

        // South stack: keyboard hint, separator, commitment | actions
        var south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));

        var hint = new JLabel("Tip: press a number or an initial to launch instantly"
            + (showIncognito ? " · hold Shift for private" : ""));
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 10.5f));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(hint);
        south.add(Box.createVerticalStrut(6));
        var separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(separator);
        south.add(Box.createVerticalStrut(8));

        // Commitment cluster (left) — its own visual group, separated from
        // the action buttons (right) so the two decisions never blur.
        var bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        var commitment = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rememberCheckbox = new JCheckBox("Always use for:");
        patternField = new JTextField(PatternMatcher.domainToPattern(domain), 18);
        patternField.setEnabled(false);
        rememberCheckbox.setMnemonic(java.awt.event.KeyEvent.VK_A);
        rememberCheckbox.setDisplayedMnemonicIndex(0);
        patternField.setToolTipText("Wildcard pattern, e.g. *.example.org or github.com/*");
        rememberCheckbox.addActionListener(e -> {
            patternField.setEnabled(rememberCheckbox.isSelected());
            validatePattern();
        });
        patternField.getDocument().addDocumentListener(docListener(this::validatePattern));
        commitment.add(rememberCheckbox);
        commitment.add(patternField);
        bottomPanel.add(commitment, BorderLayout.CENTER);

        var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        var settingsBtn = new JButton("Settings");
        settingsBtn.addActionListener(e -> openSettings());

        var cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        var openBtn = new JButton("Open");
        openBtn.addActionListener(e -> launchSelected());
        getRootPane().setDefaultButton(openBtn);
        applyAccent(openBtn);

        actions.add(settingsBtn);
        actions.add(cancelBtn);
        actions.add(openBtn);
        bottomPanel.add(actions, BorderLayout.EAST);

        south.add(bottomPanel);
        panel.add(south, BorderLayout.SOUTH);

        // Shift indicator
        addGlobalKeyListener();

        setContentPane(panel);
        pack();
        setMinimumSize(new Dimension(400, 300));
        validatePattern();
    }

    private void setupKeyBindings() {
        // ESC to close
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Launch shortcuts live on the list (WHEN_FOCUSED), so they can never
        // fire while the user is typing in the "Always use for" pattern field.
        var inputMap = browserList.getInputMap(JComponent.WHEN_FOCUSED);
        var actionMap = browserList.getActionMap();

        // Number keys 1-9 launch that row
        for (int i = 1; i <= 9 && i <= browsers.size(); i++) {
            final int index = i - 1;
            inputMap.put(KeyStroke.getKeyStroke(Character.forDigit(i, 10)), "launch-" + index);
            actionMap.put("launch-" + index, launchAction(index));
        }

        // Initial letters — the first matching row wins, so behavior stays
        // predictable when several browsers share an initial.
        var used = new HashSet<Character>();
        for (int i = 0; i < browsers.size(); i++) {
            char c = Character.toLowerCase(browsers.get(i).name().charAt(0));
            if (!used.add(c)) {
                continue;
            }
            final int index = i;
            inputMap.put(KeyStroke.getKeyStroke(c), "launch-" + index);
            inputMap.put(KeyStroke.getKeyStroke(Character.toUpperCase(c)), "launch-" + index);
            actionMap.put("launch-" + index, launchAction(index));
        }

        // Enter launches through one path — no double fire with the default button.
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "launch-enter");
        actionMap.put("launch-enter", launchAction(-1));
    }

    private Action launchAction(int index) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (index >= 0) {
                    browserList.setSelectedIndex(index);
                }
                launchSelected();
            }
        };
    }

    private void addGlobalKeyListener() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SHIFT) {
                shiftPressed = true;
                browserList.repaint();
            } else if (e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == KeyEvent.VK_SHIFT) {
                shiftPressed = false;
                browserList.repaint();
            }
            return false;
        });
    }

    private void validatePattern() {
        if (!rememberCheckbox.isSelected()) {
            patternField.putClientProperty("JComponent.outline", null);
            return;
        }
        var pattern = patternField.getText().trim();
        boolean ok = PatternMatcher.isValidPattern(pattern);
        patternField.putClientProperty("JComponent.outline", ok ? null : "error");
        patternField.setToolTipText(ok
            ? "Will always open matching links in the selected browser"
            : "Invalid pattern — use forms like *.example.org or github.com/*");
    }

    private void launchSelected() {
        var selected = browserList.getSelectedValue();
        if (selected == null) return;

        // Save rule if checkbox is selected — loudly, never silently
        if (rememberCheckbox.isSelected()) {
            var pattern = patternField.getText().trim();
            if (!PatternMatcher.isValidPattern(pattern)) {
                JOptionPane.showMessageDialog(this,
                    "“" + pattern + "” is not a valid URL pattern.\n"
                    + "Use forms like *.example.org or github.com/*,\n"
                    + "or untick \u201CAlways use for\u201D to open without saving.",
                    "Invalid Pattern",
                    JOptionPane.WARNING_MESSAGE);
                patternField.requestFocusInWindow();
                return;
            }
            db.saveRule(new UrlRule(pattern, selected.id()));
            confirmSaved(pattern, selected);
        }

        // Launch browser
        launchBrowser(selected, url, showIncognito && shiftPressed);
        dispose();
    }

    /** Confirms the saved rule with a small non-modal toast offering undo. */
    private void confirmSaved(String pattern, Browser browser) {
        var saved = db.getAllRules().stream()
            .filter(r -> r.pattern().equals(pattern) && r.browserId().equals(browser.id()))
            .findFirst();
        if (saved.isEmpty()) return;
        var ruleId = saved.get().id();
        SwingUtilities.invokeLater(() -> RuleSaveToast.show(pattern, browser, ruleId, db));
    }

    private void launchBrowser(Browser browser, String url, boolean incognito) {
        BrowserUtils.launch(browser, url, incognito, this);
    }

    private void openSettings() {
        dispose();
        SwingUtilities.invokeLater(() -> new SettingsFrame().setVisible(true));
    }

    private void centerOnScreen() {
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        // Ensure dialog gets focus when shown
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                toFront();
                requestFocus();
                browserList.requestFocusInWindow();
            }
        });
    }

    private String middleTruncate(String u, int maxLen) {
        if (u.length() <= maxLen) return u;
        int head = (int) (maxLen * 0.6);
        int tail = maxLen - head - 1;
        return u.substring(0, head) + "…" + u.substring(u.length() - tail);
    }

    private static javax.swing.event.DocumentListener docListener(Runnable r) {
        return new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        };
    }

    /** Paints the primary action in the user's Windows accent color. */
    static void applyAccent(AbstractButton button) {
        var accent = WindowsTheme.accentColor();
        if (accent == null) return;
        button.setBackground(accent);
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setFocusPainted(true);
    }

    /**
     * A small non-modal confirmation with undo, shown after an
     * “Always use for” rule is saved. The picker itself is already gone when
     * this appears, so the user learns the rule took effect — and can still
     * take it back.
     */
    static final class RuleSaveToast extends JDialog {
        private static final int AUTO_DISMISS_MS = 8000;

        private RuleSaveToast(String pattern, Browser browser, int ruleId, DatabaseService db) {
            super((Frame) null, "Rule saved");
            setUndecorated(true);
            setAlwaysOnTop(true);
            ((JRootPane) getRootPane()).setBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

            var panel = new JPanel(new BorderLayout(10, 0));
            panel.setBorder(new EmptyBorder(10, 14, 10, 14));
            panel.add(new JLabel("Always opening " + pattern + " in " + browser.name().trim()), BorderLayout.CENTER);

            var undoBtn = new JButton("Undo");
            undoBtn.addActionListener(e -> {
                db.deleteRule(ruleId);
                dispose();
            });
            panel.add(undoBtn, BorderLayout.EAST);
            setContentPane(panel);

            var timer = new javax.swing.Timer(AUTO_DISMISS_MS, e -> dispose());
            timer.setRepeats(false);
            timer.start();
        }

        private static void show(String pattern, Browser browser, int ruleId, DatabaseService db) {
            var toast = new RuleSaveToast(pattern, browser, ruleId, db);
            toast.pack();
            var screen = toast.getGraphicsConfiguration().getBounds();
            int x = screen.x + screen.width - toast.getWidth() - 24;
            int y = screen.y + screen.height - toast.getHeight() - 48;
            toast.setLocation(x, y);
            toast.setVisible(true);
        }
    }

    private class BrowserListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setBorder(new EmptyBorder(8, 10, 8, 10));

            if (value instanceof Browser browser) {
                var displayText = (index + 1) + ".  " + (browser.isProfile() ? "└ " : "") + browser.name();
                if (showIncognito && shiftPressed && browser.incognitoArg() != null) {
                    displayText += "  (Private)";
                }
                setText(displayText);
                setIcon(Icons.forBrowser(browser));

                // Profiles read as children of their browser, not look-alike siblings.
                if (browser.isProfile()) {
                    setFont(getFont().deriveFont(Font.PLAIN, getFont().getSize2D() - 1f));
                    if (!isSelected) {
                        setForeground(UIManager.getColor("Label.disabledForeground"));
                    }
                }
            }

            return this;
        }
    }
}
