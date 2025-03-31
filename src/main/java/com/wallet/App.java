package com.wallet;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.wallet.ui.MainView;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView();
        Scene scene = new Scene(mainView.getView(), 600, 400);

        // Load the CSS file
        String cssPath = null;
        try {
            cssPath = getClass().getResource("/style/main.css").toExternalForm();
            scene.getStylesheets().add(cssPath);
        } catch (NullPointerException e) {
            System.err.println("Error: CSS file not found at /style/main.css. Please ensure the file exists in the resources directory.");
        }

        stage.setTitle("Lightning Wallet");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

