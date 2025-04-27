package com.lightning;

import com.lightning.network.LightningNetworkService;
import com.lightning.ui.DashboardController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Main Application class for the Lightning Network Wallet.
 */
public class App extends Application {

    private static Scene scene;
    private LightningNetworkService lightningService;

    @Override
    public void start(Stage stage) throws IOException {
        // Initialize Lightning Network service
        lightningService = new LightningNetworkService();
        
        // Load the main FXML file
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dashboard.fxml"));
        Parent root = loader.load();
        
        // Get the controller and initialize it with the Lightning service
        DashboardController controller = loader.getController();
        controller.initialize(lightningService);
        
        // Set up the scene
        scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(getClass().getResource("/style/main.css").toExternalForm());
        
        // Configure the stage
        stage.setTitle("Lightning Network Wallet");
        
        // Load icon with proper error handling
        try {
            InputStream iconStream = getClass().getResourceAsStream("/images/lightning_icon.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                System.err.println("Warning: Could not load icon: /images/lightning_icon.png");
            }
        } catch (Exception e) {
            System.err.println("Error loading application icon: " + e.getMessage());
        }
        
        stage.setScene(scene);
        stage.setMinWidth(750);
        stage.setMinHeight(500);
        stage.show();
        
        // Handle application shutdown
        stage.setOnCloseRequest(event -> {
            if (lightningService != null) {
                lightningService.shutdown();
            }
            Platform.exit();
        });
    }
    
    /**
     * Changes the current scene to the specified FXML file
     */
    public static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    /**
     * Loads an FXML file
     */
    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/view/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    /**
     * Main method that launches the application
     */
    public static void main(String[] args) {
        launch();
    }
}