package com.lightning.ui;

import com.lightning.model.Payment;
import com.lightning.network.LightningNetworkService;
import com.lightning.util.QRCodeGenerator;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panel for sending Lightning Network payments
 */
public class PaymentPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(PaymentPanel.class.getName());
    private final LightningNetworkService lightningService;
    
    // UI components
    private JTextField paymentRequestField;
    private JTextArea paymentDetailsArea;
    private JButton decodeButton;
    private JButton payButton;
    private JLabel amountLabel;
    private JLabel statusLabel;
    private JLabel qrImageLabel;
    
    // Payment info
    private String currentPaymentRequest;
    private Payment currentPayment;

    public PaymentPanel(LightningNetworkService lightningService) {
        this.lightningService = lightningService;
        initializeUI();
    }
    
    private void initializeUI() {
        setLayout(new MigLayout("fill, insets 10", "[grow]", "[][grow]"));
        
        // Payment request input panel
        JPanel inputPanel = createInputPanel();
        add(inputPanel, "cell 0 0, growx");
        
        // Payment details panel
        JPanel detailsPanel = createDetailsPanel();
        add(detailsPanel, "cell 0 1, grow");
    }
    
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 10", "[grow][][][]", "[][]"));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Enter Payment Request",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // Payment request input field
        JLabel paymentRequestLabel = new JLabel("Payment Request (Invoice):");
        panel.add(paymentRequestLabel, "cell 0 0");
        
        paymentRequestField = new JTextField();
        panel.add(paymentRequestField, "cell 0 1, growx, span");
        
        decodeButton = new JButton("Decode");
        decodeButton.addActionListener(this::decodePaymentRequest);
        panel.add(decodeButton, "cell 1 1");
        
        payButton = new JButton("Pay");
        payButton.setEnabled(false);
        payButton.addActionListener(this::sendPayment);
        panel.add(payButton, "cell 2 1");
        
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearPaymentForm());
        panel.add(clearButton, "cell 3 1");
        
        return panel;
    }
    
    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 10", "[grow 60][grow 40]", "[][grow][]"));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "Payment Details",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // Amount label
        amountLabel = new JLabel("Amount: 0 sats");
        amountLabel.setFont(amountLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(amountLabel, "cell 0 0");
        
        // Status label
        statusLabel = new JLabel("No payment decoded");
        panel.add(statusLabel, "cell 1 0");
        
        // Left side - payment details text area
        paymentDetailsArea = new JTextArea();
        paymentDetailsArea.setEditable(false);
        paymentDetailsArea.setLineWrap(true);
        paymentDetailsArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(paymentDetailsArea);
        panel.add(scrollPane, "cell 0 1, grow");
        
        // Right side - QR code display
        qrImageLabel = new JLabel();
        qrImageLabel.setHorizontalAlignment(JLabel.CENTER);
        qrImageLabel.setBorder(BorderFactory.createEtchedBorder());
        panel.add(qrImageLabel, "cell 1 1, grow");
        
        return panel;
    }
    
    private void decodePaymentRequest(ActionEvent e) {
        String paymentRequest = paymentRequestField.getText().trim();
        if (paymentRequest.isEmpty()) {
            showError("Please enter a payment request");
            return;
        }
        
        statusLabel.setText("Decoding payment request...");
        currentPaymentRequest = paymentRequest;
        
        SwingWorker<Payment, Void> worker = new SwingWorker<>() {
            @Override
            protected Payment doInBackground() throws Exception {
                return lightningService.decodePaymentRequest(paymentRequest);
            }
            
            @Override
            protected void done() {
                try {
                    currentPayment = get();
                    displayPaymentDetails(currentPayment);
                    payButton.setEnabled(true);
                    statusLabel.setText("Payment request decoded successfully");
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.WARNING, "Failed to decode payment request", ex);
                    showError("Invalid payment request: " + ex.getMessage());
                    payButton.setEnabled(false);
                }
            }
        };
        
        worker.execute();
    }
    
    private void displayPaymentDetails(Payment payment) {
        if (payment == null) {
            clearPaymentForm();
            return;
        }
        
        // Format the amount
        DecimalFormat satFormat = new DecimalFormat("#,###");
        String amountText = satFormat.format(payment.getNumSatoshis()) + " sats";
        amountLabel.setText("Amount: " + amountText);
        
        // Set details text
        StringBuilder details = new StringBuilder();
        details.append("Payment Hash: ").append(payment.getPaymentHash()).append("\n\n");
        details.append("Destination: ").append(payment.getDestination()).append("\n\n");
        if (payment.getDescription() != null && !payment.getDescription().isEmpty()) {
            details.append("Description: ").append(payment.getDescription()).append("\n\n");
        }
        
        paymentDetailsArea.setText(details.toString());
        
        // Generate and display QR code
        try {
            // Use Swing-compatible BufferedImage
            BufferedImage qrImage = QRCodeGenerator.generateQRCode(currentPaymentRequest, 200, 200);
            ImageIcon qrIcon = new ImageIcon(qrImage);
            qrImageLabel.setIcon(qrIcon);
            qrImageLabel.setText(null);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to generate QR code", ex);
            qrImageLabel.setIcon(null);
            qrImageLabel.setText("QR code generation failed");
        }
    }
    
    private void sendPayment(ActionEvent e) {
        if (currentPayment == null || currentPaymentRequest == null) {
            showError("No valid payment request");
            return;
        }
        
        // Confirm payment
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Send payment of " + currentPayment.getValueSat() + " sats?",
            "Confirm Payment",
            JOptionPane.YES_NO_OPTION
        );
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        statusLabel.setText("Sending payment...");
        payButton.setEnabled(false);
        
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return lightningService.sendPayment(currentPaymentRequest);
            }
            
            @Override
            protected void done() {
                try {
                    Boolean success = get();
                    if (success) {
                        statusLabel.setText("Payment sent successfully");
                        JOptionPane.showMessageDialog(
                            PaymentPanel.this,
                            "Payment of " + currentPayment.getValueSat() + " sats sent successfully.",
                            "Payment Success",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                        clearPaymentForm();
                    } else {
                        statusLabel.setText("Payment failed");
                        showError("Payment failed. Please try again.");
                        payButton.setEnabled(true);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "Error sending payment", ex);
                    statusLabel.setText("Payment error");
                    showError("Error sending payment: " + ex.getMessage());
                    payButton.setEnabled(true);
                }
            }
        };
        
        worker.execute();
    }
    
    private void clearPaymentForm() {
        paymentRequestField.setText("");
        paymentDetailsArea.setText("");
        amountLabel.setText("Amount: 0 sats");
        statusLabel.setText("No payment decoded");
        payButton.setEnabled(false);
        qrImageLabel.setIcon(null);
        currentPayment = null;
        currentPaymentRequest = null;
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
     * Refreshes data - not needed for payment panel
     */
    public void refreshData() {
        // Nothing to refresh in this panel
    }
}