package com.lightning.ui;

import com.lightning.model.LightningInfo;
import com.lightning.model.WalletBalance;
import com.lightning.network.ConnectionResult;
import com.lightning.network.LightningNetworkService;
import com.lightning.wallet.PaymentHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application window for the Lightning Network Wallet application
 */
public class MainFrame extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(MainFrame.class.getName());
    private final LightningNetworkService lightningService;
    private Timer refreshTimer;
    
    // UI components
    private JTabbedPane tabbedPane;
    private DashboardPanel dashboardPanel;
    private PaymentPanel paymentPanel;
    private InvoicePanel invoicePanel;
    private SettingsPanel settingsPanel;
    private JLabel statusLabel;

    public MainFrame(LightningNetworkService lightningService) {
        super("Lightning Network Wallet");
        this.lightningService = lightningService;
        
        // Set application icon
        try {
            URL iconURL = getClass().getClassLoader().getResource("images/lightning_icon.png");
            if (iconURL != null) {
                ImageIcon icon = new ImageIcon(iconURL);
                setIconImage(icon.getImage());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to set application icon", e);
        }
        
        initializeUI();
        setupRefreshTimer();
    }
    
    private void initializeUI() {
        // Set up the main content with a border layout
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        
        // Create a toolbar at the top
        JToolBar toolBar = createToolBar();
        contentPane.add(toolBar, BorderLayout.NORTH);
        
        // Create tabbed panels for different functionality
        tabbedPane = new JTabbedPane();
        
        // Dashboard Panel
        dashboardPanel = new DashboardPanel(lightningService);
        tabbedPane.addTab("Dashboard", new ImageIcon(), dashboardPanel, "View Lightning Network node status");
        
        // Payment Panel
        paymentPanel = new PaymentPanel(lightningService);
        tabbedPane.addTab("Send Payment", new ImageIcon(), paymentPanel, "Send Lightning Network payments");
        
        // Invoice Panel
        invoicePanel = new InvoicePanel(lightningService);
        tabbedPane.addTab("Receive", new ImageIcon(), invoicePanel, "Receive payments with invoices");
        
        // Settings Panel
        settingsPanel = new SettingsPanel(lightningService);
        tabbedPane.addTab("Settings", new ImageIcon(), settingsPanel, "Application settings");
        
        contentPane.add(tabbedPane, BorderLayout.CENTER);
        
        // Status bar at the bottom
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        statusLabel = new JLabel("Ready");
        statusBar.add(statusLabel, BorderLayout.WEST);
        contentPane.add(statusBar, BorderLayout.SOUTH);
    }
    
    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(this::refreshData);
        
        JButton connectButton = new JButton("Connect Node");
        connectButton.addActionListener(e -> connectToNode());
        
        toolBar.add(refreshButton);
        toolBar.add(connectButton);
        toolBar.addSeparator();
        
        return toolBar;
    }
    
    private void refreshData(ActionEvent e) {
        updateStatus("Refreshing data...");
        
        // Update the active panel
        switch (tabbedPane.getSelectedIndex()) {
            case 0: // Dashboard
                dashboardPanel.refreshData();
                break;
            case 1: // Payment
                paymentPanel.refreshData();
                break;
            case 2: // Invoice
                invoicePanel.refreshData();
                break;
            case 3: // Settings
                // No refresh needed for settings
                break;
        }
        
        updateStatus("Data refreshed successfully");
    }
    
    private void connectToNode() {
        updateStatus("Connecting to Lightning node...");
        
        // Disable the connect button while connecting
        Component[] toolbarComponents = ((JToolBar)getContentPane().getComponent(0)).getComponents();
        JButton connectButton = null;
        for (Component c : toolbarComponents) {
            if (c instanceof JButton && ((JButton) c).getText().equals("Connect Node")) {
                connectButton = (JButton) c;
                connectButton.setEnabled(false);
                break;
            }
        }
        
        final JButton finalConnectButton = connectButton;
        
        SwingWorker<ConnectionResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ConnectionResult doInBackground() {
                ConnectionResult result = new ConnectionResult();
                try {
                    boolean connected = lightningService.connect();
                    result.setSuccess(connected);
                    
                    if (!connected) {
                        // Run diagnostics to get detailed information
                        result.setDiagnostics(lightningService.connectionDiagnostics());
                        
                        // Try to auto-fix the connection
                        result.setFixed(lightningService.autoFixConnection());
                    }
                    
                    return result;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to connect to Lightning node", e);
                    result.setSuccess(false);
                    result.setErrorMessage(e.getMessage());
                    return result;
                }
            }
            
            @Override
            protected void done() {
                try {
                    ConnectionResult result = get();
                    
                    if (result.isSuccess() || result.isFixed()) {
                        updateStatus("Connected to Lightning node successfully");
                        refreshData(null);
                    } else {
                        updateStatus("Failed to connect to Lightning node");
                        showConnectionErrorDialog(result);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error processing connection result", e);
                    updateStatus("Error connecting to Lightning node: " + e.getMessage());
                }
                
                // Re-enable the connect button
                if (finalConnectButton != null) {
                    finalConnectButton.setEnabled(true);
                }
            }
        };
        
        worker.execute();
    }
    
    private void setupRefreshTimer() {
        // Set up a timer to refresh data every 30 seconds
        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // Must update UI on EDT
                SwingUtilities.invokeLater(() -> refreshData(null));
            }
        }, 30000, 30000);
    }
    
    public void updateStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }
    
    @Override
    public void dispose() {
        // Cancel the timer when the window is closed
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
        super.dispose();
    }
    
    /**
     * Shows a detailed connection error dialog with diagnostics and options to fix
     */
    private void showConnectionErrorDialog(ConnectionResult result) {
        JTextArea diagnosticsArea = new JTextArea(15, 50);
        diagnosticsArea.setText(result.getDiagnostics());
        diagnosticsArea.setEditable(false);
        diagnosticsArea.setCaretPosition(0);
        
        JScrollPane scrollPane = new JScrollPane(diagnosticsArea);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        
        // Create a panel with options
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html><b>Connection to Lightning node failed.</b><br>Diagnostic information:</html>"), 
                 BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton runNodeButton = new JButton("Start Lightning Node");
        runNodeButton.addActionListener(e -> startLightningNode());
        buttonPanel.add(runNodeButton);
        
        JButton fixButton = new JButton("Auto-Fix Connection");
        fixButton.addActionListener(e -> autoFixConnection());
        buttonPanel.add(fixButton);
        
        JButton settingsButton = new JButton("Edit Connection Settings");
        settingsButton.addActionListener(e -> {
            tabbedPane.setSelectedIndex(3); // Switch to settings tab
            settingsPanel.focusConnectionSettings();
        });
        buttonPanel.add(settingsButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Show the dialog
        JOptionPane.showMessageDialog(
            MainFrame.this,
            panel,
            "Connection Error",
            JOptionPane.ERROR_MESSAGE
        );
    }
    
    /**
     * Start the Lightning node using the provided scripts
     */
    private void startLightningNode() {
        updateStatus("Attempting to start Lightning node...");
        
        // Try to locate the script to start the Lightning node
        String homeDir = System.getProperty("user.home");
        java.nio.file.Path scriptPath = java.nio.file.Paths.get(homeDir, "Developments", "java-dev", 
                "java-lnp-wallet", "bitcoin-lightning-dev", "setup.sh");
        
        if (java.nio.file.Files.exists(scriptPath)) {
            try {
                // Create command to run the script
                ProcessBuilder processBuilder = new ProcessBuilder("bash", scriptPath.toString());
                processBuilder.inheritIO();
                
                // Show a message that we're starting the node
                JOptionPane.showMessageDialog(
                    this,
                    "Starting Lightning node using script: " + scriptPath + "\n" +
                    "This may take a few minutes. Check terminal for progress.",
                    "Starting Lightning Node",
                    JOptionPane.INFORMATION_MESSAGE
                );
                
                // Start the process
                Process process = processBuilder.start();
                
                // Start a timer to periodically check connection
                Timer connectionTimer = new Timer(true);
                connectionTimer.scheduleAtFixedRate(new TimerTask() {
                    private int attempts = 0;
                    
                    @Override
                    public void run() {
                        attempts++;
                        // Check if we can connect now
                        try {
                            if (lightningService.testConnection() || attempts >= 12) { // Try for 2 minutes max
                                connectionTimer.cancel();
                                
                                SwingUtilities.invokeLater(() -> {
                                    updateStatus("Lightning node started successfully");
                                    refreshData(null);
                                    JOptionPane.showMessageDialog(
                                        MainFrame.this,
                                        "Lightning node is now running!",
                                        "Connection Successful",
                                        JOptionPane.INFORMATION_MESSAGE
                                    );
                                });
                            } else {
                                SwingUtilities.invokeLater(() -> 
                                    updateStatus("Waiting for Lightning node to start... (Attempt " + attempts + "/12)"));
                            }
                        } catch (Exception e) {
                            // Still waiting for connection
                            SwingUtilities.invokeLater(() -> 
                                updateStatus("Waiting for Lightning node to start... (Attempt " + attempts + "/12)"));
                        }
                    }
                }, 10000, 10000); // Check every 10 seconds
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to start Lightning node", e);
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to start Lightning node: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Could not find Lightning node startup script at: " + scriptPath + "\n" +
                "Please ensure your Bitcoin and Lightning node is installed correctly.",
                "Script Not Found",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
    
    /**
     * Attempt to auto-fix the connection using the LightningNetworkService
     */
    private void autoFixConnection() {
        updateStatus("Attempting to auto-fix connection...");
        
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return lightningService.autoFixConnection();
            }
            
            @Override
            protected void done() {
                try {
                    boolean fixed = get();
                    if (fixed) {
                        updateStatus("Connection fixed automatically!");
                        refreshData(null);
                        JOptionPane.showMessageDialog(
                            MainFrame.this,
                            "Connection to Lightning node has been fixed automatically.\n" +
                            "Configuration has been updated with working connection settings.",
                            "Connection Fixed",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        updateStatus("Could not auto-fix connection");
                        JOptionPane.showMessageDialog(
                            MainFrame.this,
                            "Could not automatically fix the connection.\n" +
                            "Please check if your Lightning node is running and try again, or edit the connection settings manually.",
                            "Auto-Fix Failed",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error auto-fixing connection", e);
                    updateStatus("Error fixing connection: " + e.getMessage());
                }
            }
        };
        
        worker.execute();
    }
}
