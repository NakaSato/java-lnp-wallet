package com.lightning.ui;

import com.lightning.model.Invoice;
import com.lightning.network.LightningNetworkService;
import com.lightning.util.QRCodeGenerator;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panel for creating and viewing Lightning Network invoices
 */
public class InvoicePanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(InvoicePanel.class.getName());
    private final LightningNetworkService lightningService;
    
    // UI components
    private JTextField amountField;
    private JTextField memoField;
    private JButton createButton;
    private JTextArea invoiceDetailsArea;
    private JLabel qrImageLabel;
    private JTable invoicesTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    
    // Current invoice
    private Invoice currentInvoice;

    public InvoicePanel(LightningNetworkService lightningService) {
        this.lightningService = lightningService;
        initializeUI();
    }
    
    private void initializeUI() {
        setLayout(new MigLayout("fill, insets 10", "[grow]", "[][grow]"));
        
        // Create invoice panel
        JPanel createInvoicePanel = createInvoiceInputPanel();
        add(createInvoicePanel, "cell 0 0, growx");
        
        // Invoice details and history panel
        JTabbedPane invoiceTabs = new JTabbedPane();
        
        JPanel invoiceDetailsPanel = createInvoiceDetailsPanel();
        invoiceTabs.addTab("Invoice Details", invoiceDetailsPanel);
        
        JPanel invoiceHistoryPanel = createInvoiceHistoryPanel();
        invoiceTabs.addTab("Invoice History", invoiceHistoryPanel);
        
        add(invoiceTabs, "cell 0 1, grow");
    }
    
    private JPanel createInvoiceInputPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[][grow][]", "[][]"));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Create Lightning Invoice",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // Amount input
        panel.add(new JLabel("Amount (sats):"), "cell 0 0");
        amountField = new JTextField("1000");
        panel.add(amountField, "cell 1 0, growx");
        
        // Memo input
        panel.add(new JLabel("Description:"), "cell 0 1");
        memoField = new JTextField("Payment for services");
        panel.add(memoField, "cell 1 1, growx");
        
        // Create button
        createButton = new JButton("Create Invoice");
        createButton.addActionListener(this::createInvoice);
        panel.add(createButton, "cell 2 0 1 2, growx");
        
        return panel;
    }
    
    private JPanel createInvoiceDetailsPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 10", "[grow 60][grow 40]", "[][grow][]"));
        panel.setBorder(BorderFactory.createEtchedBorder());
        
        // Status label
        statusLabel = new JLabel("Create an invoice to display details");
        panel.add(statusLabel, "cell 0 0 2 1");
        
        // Left side - invoice details text area
        invoiceDetailsArea = new JTextArea();
        invoiceDetailsArea.setEditable(false);
        invoiceDetailsArea.setLineWrap(true);
        invoiceDetailsArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(invoiceDetailsArea);
        panel.add(scrollPane, "cell 0 1, grow");
        
        // Right side - QR code display
        qrImageLabel = new JLabel("QR Code will appear here");
        qrImageLabel.setHorizontalAlignment(JLabel.CENTER);
        qrImageLabel.setBorder(BorderFactory.createEtchedBorder());
        panel.add(qrImageLabel, "cell 1 1, grow");
        
        // Bottom buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton copyButton = new JButton("Copy Payment Request");
        copyButton.addActionListener(e -> copyPaymentRequest());
        buttonPanel.add(copyButton);
        
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearInvoiceForm());
        buttonPanel.add(clearButton);
        
        panel.add(buttonPanel, "cell 0 2 2 1, growx");
        
        return panel;
    }
    
    private JPanel createInvoiceHistoryPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 10", "[grow]", "[grow][]"));
        panel.setBorder(BorderFactory.createEtchedBorder());
        
        // Table for invoice history
        String[] columns = {
            "Date", "Amount (sats)", "Memo", "Status", "Settle Date"
        };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make all cells non-editable
            }
        };
        invoicesTable = new JTable(tableModel);
        invoicesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        invoicesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedInvoice();
            }
        });
        
        JScrollPane tableScroll = new JScrollPane(invoicesTable);
        panel.add(tableScroll, "cell 0 0, grow");
        
        // Refresh button
        JButton refreshButton = new JButton("Refresh Invoices");
        refreshButton.addActionListener(e -> refreshInvoiceHistory());
        panel.add(refreshButton, "cell 0 1");
        
        return panel;
    }
    
    private void createInvoice(ActionEvent e) {
        try {
            long amount = Long.parseLong(amountField.getText().trim());
            if (amount <= 0) {
                showError("Amount must be greater than 0");
                return;
            }
            
            final String memo = memoField.getText().trim().isEmpty() 
                ? "Lightning Invoice" 
                : memoField.getText().trim();
            
            statusLabel.setText("Creating invoice...");
            createButton.setEnabled(false);
            
            SwingWorker<Invoice, Void> worker = new SwingWorker<>() {
                @Override
                protected Invoice doInBackground() throws Exception {
                    return lightningService.createInvoice(amount, memo);
                }
                
                @Override
                protected void done() {
                    try {
                        currentInvoice = get();
                        displayInvoiceDetails(currentInvoice);
                        statusLabel.setText("Invoice created successfully");
                        refreshInvoiceHistory();
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to create invoice", ex);
                        showError("Failed to create invoice: " + ex.getMessage());
                        statusLabel.setText("Failed to create invoice");
                    } finally {
                        createButton.setEnabled(true);
                    }
                }
            };
            
            worker.execute();
            
        } catch (NumberFormatException ex) {
            showError("Invalid amount. Please enter a valid number.");
        }
    }
    
    private void displayInvoiceDetails(Invoice invoice) {
        if (invoice == null) {
            clearInvoiceForm();
            return;
        }
        
        // Build details text
        StringBuilder details = new StringBuilder();
        details.append("Invoice Details:\n\n");
        details.append("Amount: ").append(invoice.getFormattedAmount()).append("\n");
        details.append("Creation Date: ").append(invoice.getFormattedCreationDate()).append("\n");
        details.append("Status: ").append(invoice.isSettled() ? "Paid" : "Unpaid").append("\n");
        if (invoice.isSettled()) {
            details.append("Settle Date: ").append(invoice.getFormattedSettleDate()).append("\n");
        }
        details.append("Memo: ").append(invoice.getMemo()).append("\n\n");
        details.append("Payment Request:\n").append(invoice.getPaymentRequest());
        
        invoiceDetailsArea.setText(details.toString());
        
        // Generate and display QR code
        try {
            BufferedImage qrImage = QRCodeGenerator.generateQRCode(invoice.getPaymentRequest(), 200, 200);
            ImageIcon qrIcon = new ImageIcon(qrImage);
            qrImageLabel.setIcon(qrIcon);
            qrImageLabel.setText(null);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to generate QR code", ex);
            qrImageLabel.setIcon(null);
            qrImageLabel.setText("QR code generation failed");
        }
    }
    
    private void copyPaymentRequest() {
        if (currentInvoice == null) {
            showError("No invoice available to copy");
            return;
        }
        
        String paymentRequest = currentInvoice.getPaymentRequest();
        if (paymentRequest != null && !paymentRequest.isEmpty()) {
            // Copy to clipboard
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(paymentRequest), null);
            
            statusLabel.setText("Payment request copied to clipboard");
        } else {
            showError("No payment request to copy");
        }
    }
    
    private void clearInvoiceForm() {
        amountField.setText("1000");
        memoField.setText("Payment for services");
        invoiceDetailsArea.setText("");
        qrImageLabel.setIcon(null);
        qrImageLabel.setText("QR Code will appear here");
        statusLabel.setText("Create an invoice to display details");
        currentInvoice = null;
    }
    
    private void refreshInvoiceHistory() {
        SwingWorker<List<Invoice>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Invoice> doInBackground() throws Exception {
                return lightningService.listInvoices();
            }
            
            @Override
            protected void done() {
                try {
                    List<Invoice> invoices = get();
                    updateInvoiceTable(invoices);
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to fetch invoices", ex);
                    showError("Failed to fetch invoice history: " + ex.getMessage());
                }
            }
        };
        
        worker.execute();
    }
    
    private void updateInvoiceTable(List<Invoice> invoices) {
        // Clear the table
        tableModel.setRowCount(0);
        
        if (invoices == null || invoices.isEmpty()) {
            return;
        }
        
        // Add invoices to the table
        DecimalFormat satFormat = new DecimalFormat("#,###");
        
        for (Invoice invoice : invoices) {
            Object[] row = {
                invoice.getFormattedCreationDate(),
                satFormat.format(invoice.getAmountSats()),
                invoice.getMemo(),
                invoice.isSettled() ? "Paid" : "Unpaid",
                invoice.getFormattedSettleDate()
            };
            tableModel.addRow(row);
        }
    }
    
    private void showSelectedInvoice() {
        int selectedRow = invoicesTable.getSelectedRow();
        if (selectedRow >= 0) {
            try {
                List<Invoice> invoices = lightningService.listInvoices();
                if (invoices != null && selectedRow < invoices.size()) {
                    currentInvoice = invoices.get(selectedRow);
                    displayInvoiceDetails(currentInvoice);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to load selected invoice", ex);
            }
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
    
    /**
     * Refreshes invoice data
     */
    public void refreshData() {
        refreshInvoiceHistory();
    }
}
