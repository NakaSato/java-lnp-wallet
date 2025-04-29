package com.lightning.ui;

import com.lightning.network.LightningNetworkService;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panel for application and connection settings
 */
public class SettingsPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(SettingsPanel.class.getName());
    private final LightningNetworkService lightningService;
    
    // Connection settings
    private JTextField hostField;
    private JTextField portField;
    private JPasswordField passwordField;
    private JTextField certificatePathField;
    private JCheckBox useTlsCheckbox;
    
    // Application settings
    private JComboBox<String> themeComboBox;
    private JSpinner refreshIntervalSpinner;
    
    // UI properties
    private Properties properties;
    private final String CONFIG_FILE = "lightning-config.properties";

    public SettingsPanel(LightningNetworkService lightningService) {
        this.lightningService = lightningService;
        loadProperties();
        initializeUI();
    }
    
    private void loadProperties() {
        properties = new Properties();
        
        // First load default properties
        properties.setProperty("host", "localhost");
        properties.setProperty("port", "8080");
        properties.setProperty("tls.cert.path", "");
        properties.setProperty("refresh.interval", "30");
        
        // Try to load from classpath resource
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                properties.load(in);
                LOGGER.info("Loaded configuration from classpath: " + CONFIG_FILE);
            } else {
                LOGGER.warning("Could not find config file in classpath: " + CONFIG_FILE);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load config properties from classpath", e);
        }
        
        // Try to load from user config directory if it exists (takes precedence)
        String userHome = System.getProperty("user.home");
        Path userConfigPath = Paths.get(userHome, ".lightning-wallet", CONFIG_FILE);
        
        if (Files.exists(userConfigPath)) {
            try (InputStream in = Files.newInputStream(userConfigPath)) {
                properties.load(in);
                LOGGER.info("Loaded configuration from user directory: " + userConfigPath);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load config from user directory", e);
            }
        }
    }
    
    private void initializeUI() {
        setLayout(new MigLayout("fill, insets 10", "[grow]", "[][grow][]"));
        
        // Connection settings panel
        JPanel connectionPanel = createConnectionPanel();
        add(connectionPanel, "cell 0 0, growx");
        
        // Application settings panel
        JPanel appSettingsPanel = createAppSettingsPanel();
        add(appSettingsPanel, "cell 0 1, growx");
        
        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton saveButton = new JButton("Save Settings");
        saveButton.addActionListener(e -> saveSettings());
        buttonsPanel.add(saveButton);
        
        JButton resetButton = new JButton("Reset to Defaults");
        resetButton.addActionListener(e -> resetToDefaults());
        buttonsPanel.add(resetButton);
        
        add(buttonsPanel, "cell 0 2, growx");
    }
    
    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[][grow]", "[]"));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Lightning Network Connection Settings",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // Host setting
        panel.add(new JLabel("Host:"), "cell 0 0");
        hostField = new JTextField(properties.getProperty("host", "localhost"));
        panel.add(hostField, "cell 1 0, growx");
        
        // Port setting
        panel.add(new JLabel("Port:"), "cell 0 1");
        portField = new JTextField(properties.getProperty("port", "8080"));
        panel.add(portField, "cell 1 1, growx");
        
        // Password field (if needed)
        panel.add(new JLabel("Password:"), "cell 0 2");
        passwordField = new JPasswordField();
        panel.add(passwordField, "cell 1 2, growx");
        
        // TLS settings
        panel.add(new JLabel("Use TLS:"), "cell 0 3");
        useTlsCheckbox = new JCheckBox();
        useTlsCheckbox.setSelected(!properties.getProperty("tls.cert.path", "").isEmpty());
        panel.add(useTlsCheckbox, "cell 1 3");
        
        // Certificate path
        panel.add(new JLabel("TLS Certificate Path:"), "cell 0 4");
        JPanel certPanel = new JPanel(new BorderLayout());
        certificatePathField = new JTextField(properties.getProperty("tls.cert.path", ""));
        certificatePathField.setEnabled(useTlsCheckbox.isSelected());
        certPanel.add(certificatePathField, BorderLayout.CENTER);
        
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseCertificate());
        browseButton.setEnabled(useTlsCheckbox.isSelected());
        certPanel.add(browseButton, BorderLayout.EAST);
        
        panel.add(certPanel, "cell 1 4, growx");
        
        // Enable/disable certificate fields based on TLS checkbox
        useTlsCheckbox.addActionListener(e -> {
            boolean useTls = useTlsCheckbox.isSelected();
            certificatePathField.setEnabled(useTls);
            browseButton.setEnabled(useTls);
        });
        
        return panel;
    }
    
    private JPanel createAppSettingsPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[][grow]", "[]"));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Application Settings",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // Theme selector
        panel.add(new JLabel("UI Theme:"), "cell 0 0");
        String[] themes = {"System Default", "Light", "Dark", "Dark Purple"};
        themeComboBox = new JComboBox<>(themes);
        themeComboBox.setSelectedItem("System Default"); // Default theme
        panel.add(themeComboBox, "cell 1 0, growx");
        
        // Refresh interval
        panel.add(new JLabel("Refresh Interval (seconds):"), "cell 0 1");
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(
            Integer.parseInt(properties.getProperty("refresh.interval", "30")),
            5, 300, 5);
        refreshIntervalSpinner = new JSpinner(spinnerModel);
        panel.add(refreshIntervalSpinner, "cell 1 1");
        
        return panel;
    }
    
    private void browseCertificate() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select TLS Certificate");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            certificatePathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void saveSettings() {
        try {
            // Connection settings
            properties.setProperty("host", hostField.getText().trim());
            properties.setProperty("port", portField.getText().trim());
            
            if (useTlsCheckbox.isSelected()) {
                properties.setProperty("tls.cert.path", certificatePathField.getText().trim());
            } else {
                properties.setProperty("tls.cert.path", "");
            }
            
            // Application settings
            properties.setProperty("refresh.interval", refreshIntervalSpinner.getValue().toString());
            String selectedTheme = themeComboBox.getSelectedItem().toString();
            properties.setProperty("theme", selectedTheme);
            
            // Apply the theme immediately
            applyTheme(selectedTheme);
            
            // Save to file in user's home directory
            String userHome = System.getProperty("user.home");
            Path userConfigDir = Paths.get(userHome, ".lightning-wallet");
            Path userConfigPath = userConfigDir.resolve(CONFIG_FILE);
            
            // Ensure directory exists
            if (!Files.exists(userConfigDir)) {
                Files.createDirectories(userConfigDir);
                LOGGER.info("Created user config directory: " + userConfigDir);
            }
            
            // Save to user's config file
            try (OutputStream out = Files.newOutputStream(userConfigPath)) {
                properties.store(out, "Lightning Network Wallet Settings");
                LOGGER.info("Saved configuration to: " + userConfigPath);
                
                JOptionPane.showMessageDialog(
                    this,
                    "Settings saved successfully. Some settings may require restarting the application.",
                    "Settings Saved",
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
            
            // Apply settings to the service
            lightningService.applySettings(properties);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving settings", e);
            showError("Error saving settings: " + e.getMessage());
        }
    }
    
    private void resetToDefaults() {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Reset all settings to default values?",
            "Reset Settings",
            JOptionPane.YES_NO_OPTION
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            hostField.setText("localhost");
            portField.setText("8080");
            passwordField.setText("");
            useTlsCheckbox.setSelected(false);
            certificatePathField.setText("");
            certificatePathField.setEnabled(false);
            refreshIntervalSpinner.setValue(30);
            themeComboBox.setSelectedItem("System Default");
        }
    }
    
    private void showError(String message) {
        JOptionPane.showMessageDialog(
            this,
            message,
            "Error",
            JOptionPane.ERROR_MESSAGE
        );
    }
    
    private void applyTheme(String themeName) {
        try {
            switch (themeName) {
                case "Light":
                    com.formdev.flatlaf.FlatLightLaf.setup();
                    break;
                case "Dark":
                    com.formdev.flatlaf.FlatDarkLaf.setup();
                    break;
                case "Dark Purple":
                    com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme.setup();
                    break;
                case "System Default":
                default:
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    break;
            }
            
            // Update component UI for all windows
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
            }
            
            LOGGER.log(Level.INFO, "Theme changed to: " + themeName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to apply theme: " + themeName, e);
            showError("Failed to apply theme: " + e.getMessage());
        }
    }
    
    /**
     * Refreshes data - not needed for settings panel
     */
    public void refreshData() {
        // No data to refresh
    }
}