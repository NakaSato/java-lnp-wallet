package com.lightning.ui;

import com.lightning.model.LightningInfo;
import com.lightning.model.WalletBalance;
import com.lightning.network.LightningNetworkService;
import net.miginfocom.swing.MigLayout;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dashboard panel displaying node information and wallet balances
 */
public class DashboardPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(DashboardPanel.class.getName());
    private final LightningNetworkService lightningService;
    
    // Node info components
    private JLabel nodeIdValueLabel;
    private JLabel nodeAliasValueLabel;
    private JLabel blockHeightValueLabel;
    private JLabel activeChannelsValueLabel;
    private JLabel pendingChannelsValueLabel;
    private JLabel syncStatusValueLabel;
    private JLabel connectionStatusLabel; // New connection status indicator
    private JButton connectButton; // Button to reconnect to node
    
    // Balance components
    private JLabel confirmedBalanceValueLabel;
    private JLabel unconfirmedBalanceValueLabel;
    private JLabel totalBalanceValueLabel;
    private JLabel lockedBalanceValueLabel;
    
    // Charts
    private ChartPanel balanceChartPanel;
    private DefaultPieDataset<String> balanceDataset;

    public DashboardPanel(LightningNetworkService lightningService) {
        this.lightningService = lightningService;
        initializeUI();
    }
    
    private void initializeUI() {
        setLayout(new MigLayout("fill, insets 10", "[grow]", "[grow 30][grow 70]"));
        
        // Node information section
        JPanel nodeInfoPanel = createNodeInfoPanel();
        add(nodeInfoPanel, "cell 0 0, grow");
        
        // Balance section with charts
        JPanel balancePanel = createBalancePanel();
        add(balancePanel, "cell 0 1, grow");
        
        // Initialize connection status
        updateConnectionStatus();
        
        // Set up connect button action
        connectButton.addActionListener(e -> connectToNode());
    }
    
    private JPanel createNodeInfoPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[][grow, fill]", "[]"));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Lightning Node Information",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // Create labels for node info
        panel.add(new JLabel("Node ID:"), "cell 0 0");
        nodeIdValueLabel = new JLabel("Not connected");
        panel.add(nodeIdValueLabel, "cell 1 0");
        
        panel.add(new JLabel("Node Alias:"), "cell 0 1");
        nodeAliasValueLabel = new JLabel("Not connected");
        panel.add(nodeAliasValueLabel, "cell 1 1");
        
        panel.add(new JLabel("Block Height:"), "cell 0 2");
        blockHeightValueLabel = new JLabel("0");
        panel.add(blockHeightValueLabel, "cell 1 2");
        
        panel.add(new JLabel("Active Channels:"), "cell 0 3");
        activeChannelsValueLabel = new JLabel("0");
        panel.add(activeChannelsValueLabel, "cell 1 3");
        
        panel.add(new JLabel("Pending Channels:"), "cell 0 4");
        pendingChannelsValueLabel = new JLabel("0");
        panel.add(pendingChannelsValueLabel, "cell 1 4");
        
        panel.add(new JLabel("Sync Status:"), "cell 0 5");
        syncStatusValueLabel = new JLabel("Not synced");
        panel.add(syncStatusValueLabel, "cell 1 5");
        
        // Connection status
        panel.add(new JLabel("Connection Status:"), "cell 0 6");
        connectionStatusLabel = new JLabel("Unknown");
        panel.add(connectionStatusLabel, "cell 1 6");
        
        // Connect button
        connectButton = new JButton("Connect");
        panel.add(connectButton, "cell 1 7");
        
        return panel;
    }
    
    private JPanel createBalancePanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 10", "[grow 40, fill][grow 60, fill]", "[]"));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Wallet Balance",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // Left side - balance numbers
        JPanel balanceLabelsPanel = new JPanel(new MigLayout("fillx, insets 5", "[][grow, right]", "[]"));
        
        balanceLabelsPanel.add(new JLabel("Confirmed Balance:"), "cell 0 0");
        confirmedBalanceValueLabel = new JLabel("0 sats");
        balanceLabelsPanel.add(confirmedBalanceValueLabel, "cell 1 0");
        
        balanceLabelsPanel.add(new JLabel("Unconfirmed Balance:"), "cell 0 1");
        unconfirmedBalanceValueLabel = new JLabel("0 sats");
        balanceLabelsPanel.add(unconfirmedBalanceValueLabel, "cell 1 1");
        
        balanceLabelsPanel.add(new JLabel("Total Balance:"), "cell 0 2");
        totalBalanceValueLabel = new JLabel("0 sats");
        balanceLabelsPanel.add(totalBalanceValueLabel, "cell 1 2");
        
        balanceLabelsPanel.add(new JLabel("Locked in Channels:"), "cell 0 3");
        lockedBalanceValueLabel = new JLabel("0 sats");
        balanceLabelsPanel.add(lockedBalanceValueLabel, "cell 1 3");
        
        panel.add(balanceLabelsPanel, "cell 0 0, grow");
        
        // Right side - pie chart
        balanceDataset = new DefaultPieDataset<>();
        balanceDataset.setValue("Confirmed", 0);
        balanceDataset.setValue("Unconfirmed", 0);
        balanceDataset.setValue("Locked", 0);
        
        JFreeChart balanceChart = ChartFactory.createPieChart(
            "Balance Distribution", 
            balanceDataset, 
            true, 
            true, 
            false
        );
        
        // Customize chart appearance
        PiePlot plot = (PiePlot) balanceChart.getPlot();
        plot.setBackgroundPaint(UIManager.getColor("Panel.background"));
        plot.setOutlinePaint(null);
        plot.setSectionPaint("Confirmed", new Color(0, 150, 136));
        plot.setSectionPaint("Unconfirmed", new Color(255, 193, 7));
        plot.setSectionPaint("Locked", new Color(63, 81, 181));
        
        balanceChartPanel = new ChartPanel(balanceChart);
        balanceChartPanel.setPreferredSize(new Dimension(300, 200));
        panel.add(balanceChartPanel, "cell 1 0, grow");
        
        return panel;
    }
    
    /**
     * Refreshes dashboard data from the Lightning Network
     */
    public void refreshData() {
        // Use SwingWorker to load data in background
        SwingWorker<DashboardData, Void> worker = new SwingWorker<>() {
            @Override
            protected DashboardData doInBackground() throws Exception {
                DashboardData data = new DashboardData();
                
                try {
                    data.lightningInfo = lightningService.getNodeInfo();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to fetch node info", e);
                    // Create fallback node info with default values
                    LightningInfo fallbackInfo = new LightningInfo();
                    fallbackInfo.setAlias("Not Connected");
                    fallbackInfo.setIdentityPubkey("Offline Mode - Network Unavailable");
                    fallbackInfo.setSyncedToChain(false);
                    data.lightningInfo = fallbackInfo;
                }
                
                try {
                    data.walletBalance = lightningService.getWalletBalance();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to fetch wallet balance", e);
                    // Create an empty wallet balance as fallback
                    WalletBalance fallbackBalance = new WalletBalance();
                    // Try to get last cached balance from database if available
                    data.walletBalance = fallbackBalance;
                }
                
                return data;
            }
            
            @Override
            protected void done() {
                try {
                    DashboardData data = get();
                    updateUI(data);
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.log(Level.SEVERE, "Error refreshing dashboard data", e);
                    // Show offline state in UI
                    DashboardData fallbackData = new DashboardData();
                    fallbackData.lightningInfo = createOfflineNodeInfo();
                    fallbackData.walletBalance = new WalletBalance();
                    updateUI(fallbackData);
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * Creates a default node info for offline mode
     */
    private LightningInfo createOfflineNodeInfo() {
        LightningInfo info = new LightningInfo();
        info.setAlias("Not Connected");
        info.setIdentityPubkey("Offline Mode - Connection Error");
        info.setSyncedToChain(false);
        return info;
    }
    
    private void updateUI(DashboardData data) {
        // Format for satoshi values
        DecimalFormat satFormat = new DecimalFormat("#,###");
        
        // Update node info
        if (data.lightningInfo != null) {
            nodeIdValueLabel.setText(data.lightningInfo.getIdentityPubkey());
            nodeAliasValueLabel.setText(data.lightningInfo.getAlias());
            blockHeightValueLabel.setText(String.valueOf(data.lightningInfo.getBlockHeight()));
            activeChannelsValueLabel.setText(String.valueOf(data.lightningInfo.getNumActiveChannels()));
            pendingChannelsValueLabel.setText(String.valueOf(data.lightningInfo.getNumPendingChannels()));
            
            String syncStatus = data.lightningInfo.isSyncedToChain() ? "Synced" : "Not synced";
            syncStatusValueLabel.setText(syncStatus);
        }
        
        // Update balance info
        if (data.walletBalance != null) {
            long confirmed = data.walletBalance.getConfirmedBalance();
            long unconfirmed = data.walletBalance.getUnconfirmedBalance();
            long locked = data.walletBalance.getLockedBalance();
            long total = confirmed + unconfirmed;
            
            confirmedBalanceValueLabel.setText(satFormat.format(confirmed) + " sats");
            unconfirmedBalanceValueLabel.setText(satFormat.format(unconfirmed) + " sats");
            totalBalanceValueLabel.setText(satFormat.format(total) + " sats");
            lockedBalanceValueLabel.setText(satFormat.format(locked) + " sats");
            
            // Update pie chart
            balanceDataset.setValue("Confirmed", confirmed);
            balanceDataset.setValue("Unconfirmed", unconfirmed);
            balanceDataset.setValue("Locked", locked);
        }
        
        // Update connection status after data refresh
        updateConnectionStatus();
    }
    
    /**
     * Updates the connection status indicator
     */
    private void updateConnectionStatus() {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return lightningService.testConnection();
            }
            
            @Override
            protected void done() {
                try {
                    boolean isConnected = get();
                    if (isConnected) {
                        connectionStatusLabel.setText("Connected");
                        connectionStatusLabel.setForeground(new Color(0, 150, 0)); // Green
                        connectButton.setEnabled(false);
                    } else {
                        connectionStatusLabel.setText("Disconnected");
                        connectionStatusLabel.setForeground(Color.RED);
                        connectButton.setEnabled(true);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error checking connection status", e);
                    connectionStatusLabel.setText("Unknown");
                    connectionStatusLabel.setForeground(Color.ORANGE);
                    connectButton.setEnabled(true);
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * Attempts to connect to the Lightning Network node
     */
    private void connectToNode() {
        connectButton.setEnabled(false);
        connectionStatusLabel.setText("Connecting...");
        connectionStatusLabel.setForeground(Color.BLUE);
        
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    return lightningService.connect();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error connecting to node", e);
                    return false;
                }
            }
            
            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(
                            DashboardPanel.this,
                            "Successfully connected to Lightning Network node.",
                            "Connection Successful",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                        // Refresh data now that we're connected
                        refreshData();
                    } else {
                        // Try auto-fixing the connection
                        boolean autoFixed = lightningService.autoFixConnection();
                        if (autoFixed) {
                            JOptionPane.showMessageDialog(
                                DashboardPanel.this,
                                "Auto-fixed connection to Lightning Network node.",
                                "Connection Fixed",
                                JOptionPane.INFORMATION_MESSAGE
                            );
                            // Refresh data with the fixed connection
                            refreshData();
                        } else {
                            JOptionPane.showMessageDialog(
                                DashboardPanel.this,
                                "Failed to connect to Lightning Network node.\n" +
                                "Please check your settings and ensure the node is running.",
                                "Connection Failed",
                                JOptionPane.ERROR_MESSAGE
                            );
                            connectionStatusLabel.setText("Disconnected");
                            connectionStatusLabel.setForeground(Color.RED);
                            connectButton.setEnabled(true);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error in connection process", e);
                    JOptionPane.showMessageDialog(
                        DashboardPanel.this,
                        "Error connecting to Lightning Network node: " + e.getMessage(),
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                    connectionStatusLabel.setText("Error");
                    connectionStatusLabel.setForeground(Color.RED);
                    connectButton.setEnabled(true);
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * Container class for dashboard data
     */
    private static class DashboardData {
        private LightningInfo lightningInfo;
        private WalletBalance walletBalance;
    }
}
