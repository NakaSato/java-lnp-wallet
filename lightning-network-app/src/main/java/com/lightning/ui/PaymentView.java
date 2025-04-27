package com.lightning.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class PaymentView {
    @FXML
    private TextField amountField;

    @FXML
    private TextField recipientField;

    @FXML
    private Button payButton;

    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        payButton.setOnAction(event -> initiatePayment());
    }

    private void initiatePayment() {
        String amount = amountField.getText();
        String recipient = recipientField.getText();
        
        // Simulate payment processing
        if (amount.isEmpty() || recipient.isEmpty()) {
            statusLabel.setText("Please enter both amount and recipient.");
        } else {
            statusLabel.setText("Processing payment of " + amount + " to " + recipient + "...");
            // Here you would add the logic to handle the payment
        }
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }
}