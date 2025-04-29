package com.lightning;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.intellijthemes.FlatDarkPurpleIJTheme;
import com.lightning.network.LightningNetworkService;
import com.lightning.ui.MainFrame;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main Application class for the Lightning Network Wallet.
 */
public class App {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    private LightningNetworkService lightningService;

    /**
     * Initializes and launches the Swing application
     */
    public void start() {
        // Set up the look and feel
        try {
            // Check if there's a saved theme preference
            String themeName = loadThemePreference();
            
            // Apply the appropriate theme
            switch (themeName) {
                case "Light":
                    FlatLightLaf.setup();
                    break;
                case "Dark":
                    com.formdev.flatlaf.FlatDarkLaf.setup();
                    break;
                case "Dark Purple":
                    FlatDarkPurpleIJTheme.setup();
                    break;
                case "System Default":
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    break;
                default:
                    // Default to dark purple if no preference is found
                    FlatDarkPurpleIJTheme.setup();
                    break;
            }
            
            // Set some global UI properties
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 10);
            UIManager.put("TextComponent.arc", 10);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set look and feel", e);
            // Fall back to the default look and feel
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to set system look and feel", ex);
            }
        }

        // Initialize Lightning Network service
        lightningService = new LightningNetworkService();

        // Create and show the main window
        SwingUtilities.invokeLater(() -> {
            try {
                MainFrame mainFrame = new MainFrame(lightningService);
                mainFrame.setSize(900, 600);
                mainFrame.setMinimumSize(new Dimension(750, 500));
                mainFrame.setLocationRelativeTo(null); // Center on screen
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                mainFrame.setVisible(true);
                
                // Handle application shutdown
                mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                        if (lightningService != null) {
                            lightningService.shutdown();
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to initialize application", e);
                showErrorDialog("Failed to start application: " + e.getMessage());
            }
        });
    }

    /**
     * Shows an error dialog for critical errors
     */
    private void showErrorDialog(String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                null,
                message,
                "Application Error",
                JOptionPane.ERROR_MESSAGE
            );
        });
    }

    /**
     * Load the user's theme preference from the config file
     */
    private String loadThemePreference() {
        java.util.Properties properties = new java.util.Properties();
        String configFile = "lightning-config.properties";
        
        try {
            java.io.InputStream inputStream = getClass().getClassLoader().getResourceAsStream(configFile);
            if (inputStream != null) {
                properties.load(inputStream);
                inputStream.close();
                
                // Get the theme preference, default to "Dark Purple" if not found
                return properties.getProperty("theme", "Dark Purple");
            }
        } catch (java.io.IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load theme preference", e);
        }
        
        // Default theme if configuration can't be loaded
        return "Dark Purple";
    }

    /**
     * Main method that launches the application
     */
    public static void main(String[] args) {
        // Set system properties for high DPI displays
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        App app = new App();
        app.start();
    }
}