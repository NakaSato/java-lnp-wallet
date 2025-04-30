package com.lightning.network;

import com.lightning.model.LightningInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manager class for handling Lightning Network node connections
 * This centralizes all connection functionality in one place
 */
public class LightningConnectionManager {
    private static final Logger LOGGER = Logger.getLogger(LightningConnectionManager.class.getName());
    
    private final LightningNetworkService lightningService;
    private ConnectionStatus connectionStatus;
    private String lastErrorMessage;
    private List<ConnectionListener> listeners;
    
    /**
     * Connection status enum for representing current connection state
     */
    public enum ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
        ERROR,
        UNKNOWN
    }
    
    /**
     * Interface for connection status change listeners
     */
    public interface ConnectionListener {
        void onConnectionStatusChanged(ConnectionStatus newStatus, String message);
    }
    
    /**
     * Constructor
     * @param lightningService The Lightning Network service to use
     */
    public LightningConnectionManager(LightningNetworkService lightningService) {
        this.lightningService = lightningService;
        this.connectionStatus = ConnectionStatus.UNKNOWN;
        this.lastErrorMessage = "";
        this.listeners = new ArrayList<>();
    }
    
    /**
     * Add a connection status listener
     * @param listener The listener to add
     */
    public void addConnectionListener(ConnectionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a connection status listener
     * @param listener The listener to remove
     */
    public void removeConnectionListener(ConnectionListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners of a connection status change
     * @param status The new connection status
     * @param message A message describing the status change
     */
    private void notifyListeners(ConnectionStatus status, String message) {
        this.connectionStatus = status;
        this.lastErrorMessage = message;
        
        for (ConnectionListener listener : listeners) {
            listener.onConnectionStatusChanged(status, message);
        }
    }
    
    /**
     * Check the current connection status
     * @return The current connection status
     */
    public ConnectionStatus checkConnectionStatus() {
        try {
            boolean isConnected = lightningService.testConnection();
            if (isConnected) {
                notifyListeners(ConnectionStatus.CONNECTED, "Connected to Lightning node");
                return ConnectionStatus.CONNECTED;
            } else {
                notifyListeners(ConnectionStatus.DISCONNECTED, "Disconnected from Lightning node");
                return ConnectionStatus.DISCONNECTED;
            }
        } catch (Exception e) {
            String errorMsg = "Error checking connection: " + e.getMessage();
            LOGGER.log(Level.WARNING, errorMsg, e);
            notifyListeners(ConnectionStatus.ERROR, errorMsg);
            return ConnectionStatus.ERROR;
        }
    }
    
    /**
     * Connect to the Lightning Network node
     * @return A ConnectionResult object containing the result of the connection attempt
     */
    public ConnectionResult connect() {
        ConnectionResult result = new ConnectionResult();
        
        try {
            notifyListeners(ConnectionStatus.CONNECTING, "Connecting to Lightning node...");
            
            // Attempt connection
            boolean success = lightningService.connect();
            
            if (success) {
                // Get node info to verify connection
                try {
                    LightningInfo info = lightningService.getInfo();
                    result.setSuccess(true);
                    notifyListeners(ConnectionStatus.CONNECTED, 
                            "Connected to node: " + info.getAlias() + " (" + info.getIdentityPubkey() + ")");
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Connected but failed to get node info", e);
                    result.setSuccess(true);
                    notifyListeners(ConnectionStatus.CONNECTED, "Connected to Lightning node");
                }
            } else {
                // Connection failed, run diagnostics
                String diagnostics = lightningService.connectionDiagnostics();
                result.setDiagnostics(diagnostics);
                
                // Try to auto-fix the connection
                boolean fixed = lightningService.autoFixConnection();
                result.setFixed(fixed);
                
                if (fixed) {
                    result.setSuccess(true);
                    notifyListeners(ConnectionStatus.CONNECTED, "Connection automatically fixed");
                } else {
                    result.setSuccess(false);
                    result.setErrorMessage("Failed to connect to Lightning node");
                    notifyListeners(ConnectionStatus.DISCONNECTED, "Failed to connect to Lightning node");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error connecting to Lightning node", e);
            result.setSuccess(false);
            result.setErrorMessage("Error: " + e.getMessage());
            notifyListeners(ConnectionStatus.ERROR, "Error: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Run connection diagnostics
     * @return Diagnostic information as a string
     */
    public String runDiagnostics() {
        return lightningService.connectionDiagnostics();
    }
    
    /**
     * Try to auto-fix connection issues
     * @return true if fixed successfully, false otherwise
     */
    public boolean autoFix() {
        boolean fixed = lightningService.autoFixConnection();
        if (fixed) {
            notifyListeners(ConnectionStatus.CONNECTED, "Connection automatically fixed");
        }
        return fixed;
    }
    
    /**
     * Get the connection status
     * @return The current connection status
     */
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }
    
    /**
     * Get the last error message
     * @return The last error message
     */
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
    
    /**
     * Test if a server is reachable
     * @param host The host to test
     * @param port The port to test
     * @param timeout The timeout in milliseconds
     * @return true if the server is reachable, false otherwise
     */
    public boolean testServer(String host, int port, int timeout) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Discover available Lightning nodes on the local network
     * @return A list of discovered node addresses
     */
    public List<String> discoverNodes() {
        List<String> discoveredNodes = new ArrayList<>();
        
        try {
            // Try to discover on localhost first with different ports
            String localhost = "127.0.0.1";
            int[] commonPorts = {8080, 10009, 9735, 3000, 8181, 9911};
            
            for (int port : commonPorts) {
                if (testServer(localhost, port, 500)) {
                    discoveredNodes.add(localhost + ":" + port);
                }
            }
            
            // Try to discover on local network if configured
            String localNetworkSearch = lightningService.getSetting("network.discovery", "false");
            if (Boolean.parseBoolean(localNetworkSearch)) {
                // Get local network prefix
                InetAddress localHost = InetAddress.getLocalHost();
                String hostAddress = localHost.getHostAddress();
                String prefix = hostAddress.substring(0, hostAddress.lastIndexOf('.') + 1);
                
                // Scan a range of IPs on the local network
                for (int i = 1; i < 255; i++) {
                    String host = prefix + i;
                    for (int port : commonPorts) {
                        if (testServer(host, port, 200)) {
                            discoveredNodes.add(host + ":" + port);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error discovering nodes", e);
        }
        
        return discoveredNodes;
    }
    
    /**
     * Validate connection settings
     * @param host The host to validate
     * @param port The port to validate
     * @return A message indicating if the settings are valid, or an error message
     */
    public String validateConnectionSettings(String host, String port) {
        if (host == null || host.trim().isEmpty()) {
            return "Host cannot be empty";
        }
        
        int portNum;
        try {
            portNum = Integer.parseInt(port);
            if (portNum < 1 || portNum > 65535) {
                return "Port must be between 1 and 65535";
            }
        } catch (NumberFormatException e) {
            return "Port must be a valid number";
        }
        
        // Test basic connectivity
        if (!testServer(host, portNum, 2000)) {
            return "Warning: Could not connect to " + host + ":" + port;
        }
        
        return "Valid";
    }
    
    /**
     * Save connection settings
     * @param host The host to save
     * @param port The port to save
     * @param useTls Whether to use TLS
     * @param tlsCertPath The path to the TLS certificate
     * @return true if settings were saved successfully, false otherwise
     */
    public boolean saveConnectionSettings(String host, String port, boolean useTls, String tlsCertPath) {
        try {
            Properties props = new Properties();
            props.setProperty("host", host);
            props.setProperty("port", port);
            props.setProperty("useHttps", String.valueOf(useTls));
            
            if (tlsCertPath != null && !tlsCertPath.isEmpty() && Files.exists(Path.of(tlsCertPath))) {
                props.setProperty("tls.cert.path", tlsCertPath);
            }
            
            lightningService.applySettings(props);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving connection settings", e);
            return false;
        }
    }
    
    /**
     * Find TLS certificate files in common locations
     * @return A list of found TLS certificate file paths
     */
    public List<String> findTlsCertificates() {
        List<String> certPaths = new ArrayList<>();
        String homeDir = System.getProperty("user.home");
        
        // Check common locations
        List<Path> possiblePaths = new ArrayList<>();
        possiblePaths.add(Paths.get(homeDir, "Developments", "java-dev", "java-lnp-wallet", 
                "bitcoin-lightning-dev", "data", "lightning", "tls.cert"));
        possiblePaths.add(Paths.get(homeDir, ".lnd", "tls.cert"));
        possiblePaths.add(Paths.get(homeDir, ".lightning", "tls.cert"));
        possiblePaths.add(Paths.get(homeDir, "Library", "Application Support", "Lnd", "tls.cert"));
        possiblePaths.add(Paths.get(homeDir, ".polar", "networks", "1", "volumes", "lnd", "tls.cert"));
        
        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                certPaths.add(path.toString());
            }
        }
        
        return certPaths;
    }
}