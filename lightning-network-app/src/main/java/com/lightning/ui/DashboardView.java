package com.lightning.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class DashboardView {
    @FXML
    private VBox dashboardContainer;

    @FXML
    private Label balanceLabel;

    @FXML
    private Label transactionHistoryLabel;

    public void initialize() {
        // Initialize the dashboard with wallet balances and transaction history
        balanceLabel.setText("Balance: $0.00");
        transactionHistoryLabel.setText("Transaction History: None");
    }

    public VBox getView() {
        return dashboardContainer;
    }
}