package com.wallet.ui;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Button;

public class MainView {
    private VBox vbox;

    public MainView() {
        vbox = new VBox();
        vbox.getStyleClass().add("vbox"); // Add CSS class for VBox

        // Create new instances of nodes
        Label label = new Label("Welcome to Lightning Wallet");
        label.getStyleClass().add("label"); // Add CSS class for Label

        Button button1 = new Button("Button 1");
        button1.getStyleClass().add("button"); // Add CSS class for Button

        Button button2 = new Button("Button 2");
        button2.getStyleClass().add("button"); // Add CSS class for Button

        // Add nodes to the VBox
        vbox.getChildren().addAll(label, button1, button2);
    }

    public VBox getView() {
        return vbox;
    }
}
