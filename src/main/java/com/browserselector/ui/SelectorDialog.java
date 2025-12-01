package com.browserselector.ui;

import com.browserselector.model.Browser;
import com.browserselector.model.UrlRule;
import com.browserselector.service.DatabaseService;
import com.browserselector.util.PatternMatcher;
import com.browserselector.util.UrlUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class SelectorDialog extends JDialog {

    private final String url;
    private final String domain;
    private final DatabaseService db;
    private final List<Browser> browsers;
    private final JFrame ownerFrame;

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
        frame.setVisible(true);
        return frame;
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        var panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // URL display
        var urlLabel = new JLabel(truncateUrl(url, 60));
        urlLabel.setFont(urlLabel.getFont().deriveFont(Font.PLAIN, 11f));
        urlLabel.setForeground(Color.GRAY);
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

        browserList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    launchSelected();
                } else if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    shiftPressed = true;
                    browserList.repaint();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    shiftPressed = false;
                    browserList.repaint();
                }
            }
        });

        var scrollPane = new JScrollPane(browserList);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Bottom panel
        var bottomPanel = new JPanel(new BorderLayout(5, 5));

        // Remember checkbox and pattern field
        var rememberPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rememberCheckbox = new JCheckBox("Always use for:");
        patternField = new JTextField(PatternMatcher.domainToPattern(domain), 20);
        patternField.setEnabled(false);

        rememberCheckbox.addActionListener(e -> patternField.setEnabled(rememberCheckbox.isSelected()));

        rememberPanel.add(rememberCheckbox);
        rememberPanel.add(patternField);
        bottomPanel.add(rememberPanel, BorderLayout.CENTER);

        // Buttons
        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        var settingsBtn = new JButton("Settings");
        settingsBtn.addActionListener(e -> openSettings());

        var cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        var openBtn = new JButton("Open");
        openBtn.addActionListener(e -> launchSelected());
        getRootPane().setDefaultButton(openBtn);

        buttonPanel.add(settingsBtn);
        buttonPanel.add(cancelBtn);
        buttonPanel.add(openBtn);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        // Shift indicator
        addGlobalKeyListener();

        setContentPane(panel);
        pack();
        setMinimumSize(new Dimension(400, 300));
    }

    private void setupKeyBindings() {
        // ESC to close
        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Number keys 1-9 to select browser
        for (int i = 1; i <= 9 && i <= browsers.size(); i++) {
            var index = i - 1;
            getRootPane().registerKeyboardAction(
                e -> {
                    browserList.setSelectedIndex(index);
                    launchSelected();
                },
                KeyStroke.getKeyStroke(Character.forDigit(i, 10), 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }

        // First letter of browser name
        for (int i = 0; i < browsers.size(); i++) {
            var browser = browsers.get(i);
            var firstChar = Character.toLowerCase(browser.name().charAt(0));
            var index = i;
            getRootPane().registerKeyboardAction(
                e -> {
                    browserList.setSelectedIndex(index);
                    launchSelected();
                },
                KeyStroke.getKeyStroke(firstChar),
                JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }
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

    private void launchSelected() {
        var selected = browserList.getSelectedValue();
        if (selected == null) return;

        // Save rule if checkbox is selected
        if (rememberCheckbox.isSelected()) {
            var pattern = patternField.getText().trim();
            if (PatternMatcher.isValidPattern(pattern)) {
                db.saveRule(new UrlRule(pattern, selected.id()));
            }
        }

        // Launch browser
        launchBrowser(selected, url, shiftPressed);
        dispose();
    }

    private void launchBrowser(Browser browser, String url, boolean incognito) {
        try {
            var command = new java.util.ArrayList<String>();
            command.add(browser.exePath().toString());

            if (browser.profileArg() != null && !browser.profileArg().isBlank()) {
                command.add(browser.profileArg());
            }

            if (incognito && browser.incognitoArg() != null) {
                command.add(browser.incognitoArg());
            }

            command.add(url);

            new ProcessBuilder(command).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Failed to launch browser: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
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

    private String truncateUrl(String url, int maxLen) {
        if (url.length() <= maxLen) return url;
        return url.substring(0, maxLen - 3) + "...";
    }

    private class BrowserListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof Browser browser) {
                var displayText = (index + 1) + ". " + browser.displayName();
                if (shiftPressed && browser.incognitoArg() != null) {
                    displayText += " (Private)";
                }
                setText(displayText);

                // Load icon if available
                if (browser.iconPath() != null && Files.exists(browser.iconPath())) {
                    try {
                        var icon = new ImageIcon(browser.iconPath().toString());
                        var scaled = icon.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH);
                        setIcon(new ImageIcon(scaled));
                    } catch (Exception e) {
                        setIcon(UIManager.getIcon("FileView.computerIcon"));
                    }
                } else {
                    setIcon(UIManager.getIcon("FileView.computerIcon"));
                }

                setBorder(new EmptyBorder(8, 10, 8, 10));
            }

            return this;
        }
    }
}
