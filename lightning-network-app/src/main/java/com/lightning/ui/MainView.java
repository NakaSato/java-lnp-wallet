package com.lightning.ui;

import javafx.scene.layout.BorderPane;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public class MainView {
    private BorderPane layout;

    public MainView() {
        layout = new BorderPane();
        setupUI();
    }

    private void setupUI() {
        VBox vbox = new VBox();
        Label titleLabel = new Label("Welcome to the Lightning Wallet");
        Button dashboardButton = new Button("Go to Dashboard");
        Button paymentButton = new Button("Make a Payment");

        vbox.getChildren().addAll(titleLabel, dashboardButton, paymentButton);
        layout.setCenter(vbox);
    }

    public BorderPane getView() {
        return layout;
    }
}