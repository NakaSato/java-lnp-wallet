package com.lightning.ui;

import com.lightning.model.LightningInfo;
import com.lightning.model.WalletBalance;
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
        
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    return lightningService.connect();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to connect to Lightning node", e);
                    return false;
                }
            }
            
            @Override
            protected void done() {
                try {
                    Boolean success = get();
                    if (success) {
                        updateStatus("Connected to Lightning node");
                        refreshData(null);
                    } else {
                        updateStatus("Failed to connect to Lightning node");
                        JOptionPane.showMessageDialog(
                            MainFrame.this,
                            "Failed to connect to Lightning node. Check your connection settings.",
                            "Connection Error",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error processing connection result", e);
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
}
