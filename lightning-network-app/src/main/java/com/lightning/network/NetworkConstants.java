package com.lightning.network;

/**
 * Constants for Bitcoin and Lightning Network connectivity
 */
public class NetworkConstants {
    // Bitcoin RPC endpoint
    public static final String BITCOIN_RPC_URL = "http://localhost:18443";
    
    // Lightning Network RPC endpoint
    public static final String LIGHTNING_RPC_URL = "http://localhost:10009";
    
    // Lightning Network REST API endpoint
    public static final String LIGHTNING_REST_API_URL = "https://localhost:8080";
    
    // Connection timeout in seconds
    public static final int CONNECTION_TIMEOUT = 30;
    
    // Default endpoints
    public static final String DEFAULT_BITCOIN_HOST = "localhost";
    public static final int DEFAULT_BITCOIN_PORT = 18443;
    public static final String DEFAULT_LIGHTNING_HOST = "localhost";
    public static final int DEFAULT_LIGHTNING_RPC_PORT = 10009;
    public static final int DEFAULT_LIGHTNING_REST_PORT = 8080;
    
    // Protocol prefixes
    public static final String HTTP_PREFIX = "http://";
    public static final String HTTPS_PREFIX = "https://";
    
    /**
     * Get the full URL for Bitcoin RPC endpoint
     * @param host Host address
     * @param port Port number
     * @return Full URL
     */
    public static String getBitcoinRpcUrl(String host, int port) {
        return HTTP_PREFIX + host + ":" + port;
    }
    
    /**
     * Get the full URL for Lightning Network RPC endpoint
     * @param host Host address
     * @param port Port number
     * @return Full URL
     */
    public static String getLightningRpcUrl(String host, int port) {
        return HTTP_PREFIX + host + ":" + port;
    }
    
    /**
     * Get the full URL for Lightning Network REST API endpoint
     * @param host Host address
     * @param port Port number
     * @param useTls Whether to use HTTPS
     * @return Full URL
     */
    public static String getLightningRestUrl(String host, int port, boolean useTls) {
        return (useTls ? HTTPS_PREFIX : HTTP_PREFIX) + host + ":" + port + "/v1";
    }
}
