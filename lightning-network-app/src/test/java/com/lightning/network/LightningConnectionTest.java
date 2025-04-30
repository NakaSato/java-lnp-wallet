package com.lightning.network;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test class for the Lightning Connection Manager
 * Demonstrates how to use the connection manager in your application
 */
public class LightningConnectionTest {
    private static final Logger LOGGER = Logger.getLogger(LightningConnectionTest.class.getName());
    
    public static void main(String[] args) {
        // Create the Lightning Network Service
        LightningNetworkService lightningService = new LightningNetworkService();
        
        // Create the Connection Manager
        LightningConnectionManager connectionManager = new LightningConnectionManager(lightningService);
        
        // Add a connection listener
        connectionManager.addConnectionListener((status, message) -> {
            LOGGER.info("Connection status changed: " + status + " - " + message);
            
            // You can update UI elements here based on status
            switch (status) {
                case CONNECTED:
                    System.out.println("✅ Connected: " + message);
                    break;
                case DISCONNECTED:
                    System.out.println("❌ Disconnected: " + message);
                    break;
                case CONNECTING:
                    System.out.println("🔄 Connecting: " + message);
                    break;
                case ERROR:
                    System.out.println("⚠️ Error: " + message);
                    break;
                default:
                    System.out.println("❓ Unknown status: " + message);
            }
        });
        
        // Example 1: Check connection status
        LightningConnectionManager.ConnectionStatus status = connectionManager.checkConnectionStatus();
        System.out.println("Initial connection status: " + status);
        
        // Example 2: Connect to Lightning node
        if (status != LightningConnectionManager.ConnectionStatus.CONNECTED) {
            System.out.println("Attempting to connect to Lightning node...");
            ConnectionResult result = connectionManager.connect();
            
            if (result.isSuccess()) {
                System.out.println("Successfully connected to Lightning node");
            } else {
                System.out.println("Failed to connect to Lightning node: " + result.getErrorMessage());
                System.out.println("Diagnostics: " + result.getDiagnostics());
                
                if (result.isFixed()) {
                    System.out.println("Connection was automatically fixed");
                } else {
                    System.out.println("Trying to auto-fix connection...");
                    boolean fixed = connectionManager.autoFix();
                    System.out.println("Auto-fix " + (fixed ? "succeeded" : "failed"));
                }
            }
        }
        
        // Example 3: Discover available Lightning nodes
        System.out.println("Discovering available Lightning nodes...");
        List<String> nodes = connectionManager.discoverNodes();
        if (nodes.isEmpty()) {
            System.out.println("No Lightning nodes found");
        } else {
            System.out.println("Found " + nodes.size() + " Lightning nodes:");
            for (String node : nodes) {
                System.out.println("  - " + node);
            }
        }
        
        // Example 4: Find TLS certificates
        System.out.println("Finding TLS certificates...");
        List<String> certs = connectionManager.findTlsCertificates();
        if (certs.isEmpty()) {
            System.out.println("No TLS certificates found");
        } else {
            System.out.println("Found " + certs.size() + " TLS certificates:");
            for (String cert : certs) {
                System.out.println("  - " + cert);
            }
        }
        
        // Example 5: Validate connection settings
        String host = "localhost";
        String port = "8080";
        System.out.println("Validating connection settings: " + host + ":" + port);
        String validationResult = connectionManager.validateConnectionSettings(host, port);
        System.out.println("Validation result: " + validationResult);
        
        // Clean up
        lightningService.shutdown();
    }
}public class LightningConnectionTest {
  
}
