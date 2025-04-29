package com.lightning.ui;

import com.lightning.model.Invoice;
import com.lightning.model.LightningInfo;
import com.lightning.model.Payment;
import com.lightning.model.WalletBalance;
import com.lightning.network.LightningNetworkService;
import com.lightning.util.QRCodeGenerator;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the main dashboard screen of the Lightning Network wallet
 */
public class DashboardController {
    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());
    
    @FXML private Label nodeIdLabel;
    @FXML private Label nodeAliasLabel;
    @FXML private Label channelsLabel;
    @FXML private Label pendingChannelsLabel;
    @FXML private Label peersLabel;
    @FXML private Label blockHeightLabel;
    @FXML private Label syncStatusLabel;
    @FXML private Label connectionStatusLabel; // Connection status indicator
    @FXML private Button connectButton; // Button to reconnect to node
    
    @FXML private Label walletBalanceLabel;
    @FXML private Label confirmedBalanceLabel;
    @FXML private Label unconfirmedBalanceLabel;
    
    @FXML private TextField receiveAmountField;
    @FXML private TextField receiveMemoField;
    @FXML private TextField receiveInvoiceField;
    @FXML private ImageView qrCodeImageView;
    
    @FXML private TextField payInvoiceField;
    
    @FXML private TableView<Invoice> invoicesTable;
    @FXML private TableColumn<Invoice, String> memoColumn;
    @FXML private TableColumn<Invoice, String> amountColumn;
    @FXML private TableColumn<Invoice, String> statusColumn;
    @FXML private TableColumn<Invoice, String> dateColumn;
    
    @FXML private VBox receiveTab;
    @FXML private VBox payTab;
    @FXML private VBox transactionsTab;
    
    private LightningNetworkService lightningService;
    private ObservableList<Invoice> invoices = FXCollections.observableArrayList();
    private ScheduledExecutorService refreshExecutor;
    
    /**
     * Initialize the controller with the Lightning Network service
     */
    public void initialize(LightningNetworkService lightningService) {
        this.lightningService = lightningService;
        
        // Set up the invoices table columns
        memoColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMemo()));
        amountColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedAmount()));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isSettled() ? "Settled" : "Pending"));
        dateColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedCreationDate()));
        
        invoicesTable.setItems(invoices);
        
        // Set up context menu for the invoices table
        setupInvoicesTableContextMenu();
        
        // Set up connect button action
        if (connectButton != null) {
            connectButton.setOnAction(event -> connectToNode());
            
            // Style the connection status label
            connectionStatusLabel.setStyle("-fx-font-weight: bold;");
        }
        
        // Start a background task to update node info and wallet balance periodically
        startBackgroundRefresh();
        
        // Initial data load
        refreshNodeInfo();
        refreshWalletBalance();
        refreshInvoices();
        checkConnectionStatus(); // Check initial connection status
    }
    
    /**
     * Start a background task to periodically refresh the node info and wallet balance
     */
    private void startBackgroundRefresh() {
        refreshExecutor = Executors.newSingleThreadScheduledExecutor();
        refreshExecutor.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> {
                refreshNodeInfo();
                refreshWalletBalance();
                refreshInvoices();
            });
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Clean up resources when the controller is no longer needed
     */
    public void shutdown() {
        if (refreshExecutor != null) {
            refreshExecutor.shutdown();
        }
    }
    
    /**
     * Refresh the node information
     */
    @FXML
    private void refreshNodeInfo() {
        Task<LightningInfo> task = new Task<>() {
            @Override
            protected LightningInfo call() throws Exception {
                return lightningService.getInfo();
            }
        };
        
        task.setOnSucceeded(event -> {
            LightningInfo info = task.getValue();
            nodeIdLabel.setText(info.getIdentityPubkey());
            nodeAliasLabel.setText(info.getAlias());
            channelsLabel.setText(String.valueOf(info.getNumActiveChannels()));
            pendingChannelsLabel.setText(String.valueOf(info.getNumPendingChannels()));
            peersLabel.setText(String.valueOf(info.getNumPeers()));
            blockHeightLabel.setText(String.valueOf(info.getBlockHeight()));
            syncStatusLabel.setText(info.isSyncedToChain() ? "Synced" : "Syncing...");
        });
        
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            LOGGER.log(Level.SEVERE, "Error refreshing node info", ex);
            showError("Error", "Failed to refresh node information", ex.getMessage());
        });
        
        new Thread(task).start();
    }
    
    /**
     * Refresh the wallet balance
     */
    @FXML
    private void refreshWalletBalance() {
        Task<WalletBalance> task = new Task<>() {
            @Override
            protected WalletBalance call() throws Exception {
                try {
                    return lightningService.getWalletBalance();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error fetching wallet balance from network", e);
                    // Return a fallback balance from local database or empty balance object
                    WalletBalance fallbackBalance = new WalletBalance();
                    // Try to get last known balance from database if possible
                    return fallbackBalance;
                }
            }
        };
        
        task.setOnSucceeded(event -> {
            WalletBalance balance = task.getValue();
            walletBalanceLabel.setText(balance.formatTotalBalanceBtc() + " BTC");
            confirmedBalanceLabel.setText(WalletBalance.formatSatsToBtc(balance.getConfirmedBalance()) + " BTC");
            unconfirmedBalanceLabel.setText(WalletBalance.formatSatsToBtc(balance.getUnconfirmedBalance()) + " BTC");
        });
        
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            LOGGER.log(Level.SEVERE, "Error refreshing wallet balance", ex);
            showError("Error", "Failed to refresh wallet balance", ex.getMessage());
            
            // Show empty balance instead of error
            WalletBalance emptyBalance = new WalletBalance();
            walletBalanceLabel.setText(emptyBalance.formatTotalBalanceBtc() + " BTC");
            confirmedBalanceLabel.setText(WalletBalance.formatSatsToBtc(emptyBalance.getConfirmedBalance()) + " BTC");
            unconfirmedBalanceLabel.setText(WalletBalance.formatSatsToBtc(emptyBalance.getUnconfirmedBalance()) + " BTC");
        });
        
        new Thread(task).start();
    }
    
    /**
     * Refresh the list of invoices - use local database as backup
     */
    @FXML
    private void refreshInvoices() {
        Task<List<Invoice>> task = new Task<>() {
            @Override
            protected List<Invoice> call() throws Exception {
                try {
                    // Check if gRPC proxy is available at 127.0.0.1:8080
                    boolean useGrpcProxy = lightningService.isGrpcProxyAvailable();
                    if (useGrpcProxy) {
                        LOGGER.info("Using gRPC proxy for invoice refresh");
                        // Use gRPC method when available
                        return lightningService.listInvoicesViaGrpc();
                    } else {
                        // Fall back to REST API
                        LOGGER.info("Using REST API for invoice refresh");
                        return lightningService.listInvoices();
                    }
                } catch (Exception e) {
                    // If that fails, fall back to local database
                    LOGGER.log(Level.WARNING, "Could not fetch invoices from node, using local database", e);
                    return lightningService.getLocalInvoices();
                }
            }
        };
        
        task.setOnSucceeded(event -> {
            List<Invoice> newInvoices = task.getValue();
            invoices.clear();
            invoices.addAll(newInvoices);
        });
        
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            LOGGER.log(Level.SEVERE, "Error refreshing invoices", ex);
            showError("Error", "Failed to refresh invoices", ex.getMessage());
        });
        
        new Thread(task).start();
    }
    
    /**
     * Generate a new invoice for receiving payment
     */
    @FXML
    private void generateInvoice() {
        String amountText = receiveAmountField.getText().trim();
        String memo = receiveMemoField.getText().trim();
        
        if (amountText.isEmpty()) {
            showError("Error", "Invalid Amount", "Please enter a valid amount in satoshis");
            return;
        }
        
        long amount;
        try {
            amount = Long.parseLong(amountText);
            if (amount <= 0) {
                throw new NumberFormatException("Amount must be positive");
            }
        } catch (NumberFormatException e) {
            showError("Error", "Invalid Amount", "Please enter a valid amount in satoshis");
            return;
        }
        
        if (memo.isEmpty()) {
            memo = "Payment to " + nodeAliasLabel.getText();
        }
        
        final String finalMemo = memo;
        
        Task<Invoice> task = new Task<>() {
            @Override
            protected Invoice call() throws Exception {
                // Use the database-integrated method instead
                return lightningService.createInvoiceAndSave(amount, finalMemo);
            }
        };
        
        task.setOnSucceeded(event -> {
            Invoice invoice = task.getValue();
            receiveInvoiceField.setText(invoice.getPaymentRequest());
            qrCodeImageView.setImage(QRCodeGenerator.generateQRCodeFX(invoice.getPaymentRequest(), 250, 250));
            
            // Update the invoice list
            refreshInvoices();
        });
        
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            LOGGER.log(Level.SEVERE, "Error generating invoice", ex);
            showError("Error", "Failed to generate invoice", ex.getMessage());
        });
        
        new Thread(task).start();
    }
    
    /**
     * Pay a Lightning invoice
     */
    @FXML
    private void payInvoice() {
        String paymentRequest = payInvoiceField.getText().trim();
        
        if (paymentRequest.isEmpty()) {
            showError("Error", "Invalid Invoice", "Please enter a valid Lightning invoice");
            return;
        }
        
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Payment");
        confirmDialog.setHeaderText("Are you sure you want to pay this invoice?");
        confirmDialog.setContentText("Payment Request: " + paymentRequest);
        
        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<Payment> task = new Task<>() {
                @Override
                protected Payment call() throws Exception {
                    // Use the database-integrated method instead
                    return lightningService.payInvoiceAndSave(paymentRequest);
                }
            };
            
            task.setOnSucceeded(event -> {
                Payment payment = task.getValue();
                payInvoiceField.clear();
                
                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Payment Successful");
                successAlert.setHeaderText("Payment sent successfully");
                successAlert.setContentText(String.format(
                        "Amount: %s\nFee: %s\nPayment Hash: %s", 
                        payment.getFormattedAmount(), 
                        payment.getFormattedFee(),
                        payment.getPaymentHash()));
                successAlert.showAndWait();
                
                // Refresh data after payment
                refreshWalletBalance();
            });
            
            task.setOnFailed(event -> {
                Throwable ex = task.getException();
                LOGGER.log(Level.SEVERE, "Error paying invoice", ex);
                showError("Error", "Failed to pay invoice", ex.getMessage());
            });
            
            new Thread(task).start();
        }
    }
    
    /**
     * Generate a new Bitcoin address for depositing funds
     */
    @FXML
    private void generateNewAddress() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return lightningService.getNewAddress();
            }
        };
        
        task.setOnSucceeded(event -> {
            String address = task.getValue();
            
            TextInputDialog dialog = new TextInputDialog(address);
            dialog.setTitle("Deposit Bitcoin");
            dialog.setHeaderText("Your Bitcoin Deposit Address");
            dialog.setContentText("Send funds to this address to fund your Lightning wallet:");
            
            ImageView qrImageView = new ImageView(QRCodeGenerator.generateQRCodeFX(address, 200, 200));
            qrImageView.setFitWidth(200);
            qrImageView.setFitHeight(200);
            
            VBox content = new VBox(10);
            content.getChildren().addAll(
                    new Label("Scan this QR code to deposit Bitcoin:"),
                    qrImageView,
                    new Label("Address:"));
            
            dialog.getDialogPane().setContent(content);
            dialog.showAndWait();
        });
        
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            LOGGER.log(Level.SEVERE, "Error generating new address", ex);
            showError("Error", "Failed to generate new Bitcoin address", ex.getMessage());
        });
        
        new Thread(task).start();
    }
    
    /**
     * Copy the payment request to clipboard
     */
    @FXML
    private void copyInvoice() {
        String invoice = receiveInvoiceField.getText();
        if (invoice != null && !invoice.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(invoice);
            clipboard.setContent(content);
            
            // Show a tooltip or some indication that it was copied
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Invoice Copied");
            alert.setHeaderText(null);
            alert.setContentText("The payment request has been copied to clipboard.");
            alert.showAndWait();
        }
    }
    
    /**
     * Setup context menu for the invoices table
     */
    private void setupInvoicesTableContextMenu() {
        invoicesTable.setRowFactory(tv -> {
            TableRow<Invoice> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();
            
            MenuItem copyItem = new MenuItem("Copy Payment Request");
            copyItem.setOnAction(event -> {
                Invoice invoice = row.getItem();
                if (invoice != null) {
                    javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(invoice.getPaymentRequest());
                    clipboard.setContent(content);
                }
            });
            
            contextMenu.getItems().add(copyItem);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu));
            
            return row;
        });
    }
    
    /**
     * Show an error dialog with the given details
     */
    private void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    /**
     * Checks the connection status to the Lightning Network node
     */
    private void checkConnectionStatus() {
        if (connectionStatusLabel == null || connectButton == null) {
            return; // UI elements not available
        }
        
        // Set initial state
        connectionStatusLabel.setText("Checking...");
        connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
        connectButton.setDisable(true);
        
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return lightningService.testConnection();
            }
        };
        
        task.setOnSucceeded(event -> {
            boolean isConnected = task.getValue();
            if (isConnected) {
                connectionStatusLabel.setText("Connected");
                connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
                connectButton.setDisable(true);
            } else {
                connectionStatusLabel.setText("Disconnected");
                connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
                connectButton.setDisable(false);
            }
        });
        
        task.setOnFailed(event -> {
            connectionStatusLabel.setText("Error");
            connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
            connectButton.setDisable(false);
            LOGGER.log(Level.WARNING, "Error checking connection status", task.getException());
        });
        
        new Thread(task).start();
    }
    
    /**
     * Connect to the Lightning Network node
     */
    @FXML
    private void connectToNode() {
        if (connectionStatusLabel == null || connectButton == null) {
            return; // UI elements not available
        }
        
        connectionStatusLabel.setText("Connecting...");
        connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
        connectButton.setDisable(true);
        
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    return lightningService.connect();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error connecting to Lightning node", e);
                    return false;
                }
            }
        };
        
        task.setOnSucceeded(event -> {
            boolean success = task.getValue();
            if (success) {
                // Successfully connected
                connectionStatusLabel.setText("Connected");
                connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Connection Successful");
                alert.setHeaderText("Successfully connected to Lightning Network node");
                alert.setContentText("The wallet is now connected to the Lightning Network node.");
                alert.showAndWait();
                
                // Refresh data
                refreshNodeInfo();
                refreshWalletBalance();
                refreshInvoices();
            } else {
                // Try auto-fixing
                Task<Boolean> autoFixTask = new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        return lightningService.autoFixConnection();
                    }
                };
                
                autoFixTask.setOnSucceeded(fixEvent -> {
                    boolean fixed = autoFixTask.getValue();
                    if (fixed) {
                        connectionStatusLabel.setText("Connected");
                        connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
                        
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Connection Fixed");
                        alert.setHeaderText("Auto-fixed Lightning Network connection");
                        alert.setContentText("The wallet automatically fixed the connection to the Lightning Network node.");
                        alert.showAndWait();
                        
                        // Refresh data
                        refreshNodeInfo();
                        refreshWalletBalance();
                        refreshInvoices();
                    } else {
                        connectionStatusLabel.setText("Disconnected");
                        connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
                        connectButton.setDisable(false);
                        
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Connection Failed");
                        alert.setHeaderText("Failed to connect to Lightning Network node");
                        alert.setContentText("Please check your connection settings and ensure the Lightning Network node is running.\n\n" + 
                                            "You can run the connection diagnostics to troubleshoot the issue.");
                        alert.showAndWait();
                    }
                });
                
                autoFixTask.setOnFailed(fixEvent -> {
                    connectionStatusLabel.setText("Error");
                    connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
                    connectButton.setDisable(false);
                    
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Connection Error");
                    alert.setHeaderText("Error connecting to Lightning Network node");
                    alert.setContentText("An error occurred while trying to connect to the node: " + 
                                        autoFixTask.getException().getMessage());
                    alert.showAndWait();
                });
                
                new Thread(autoFixTask).start();
            }
        });
        
        task.setOnFailed(event -> {
            connectionStatusLabel.setText("Error");
            connectionStatusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
            connectButton.setDisable(false);
            
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection Error");
            alert.setHeaderText("Error connecting to Lightning Network node");
            alert.setContentText("An error occurred while trying to connect to the node: " + 
                                task.getException().getMessage());
            alert.showAndWait();
        });
        
        new Thread(task).start();
    }
    
    /**
     * Run Lightning Network connection diagnostics
     */
    @FXML
    private void runConnectionDiagnostics() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return lightningService.connectionDiagnostics();
            }
        };
        
        task.setOnSucceeded(event -> {
            String report = task.getValue();
            
            TextArea textArea = new TextArea(report);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefHeight(400);
            textArea.setPrefWidth(600);
            
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Connection Diagnostics");
            dialog.setHeaderText("Lightning Network Connection Diagnostics");
            
            ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().add(closeButton);
            dialog.getDialogPane().setContent(textArea);
            dialog.showAndWait();
        });
        
        task.setOnFailed(event -> {
            showError("Diagnostics Error", "Failed to run connection diagnostics", 
                     task.getException().getMessage());
        });
        
        new Thread(task).start();
    }
}