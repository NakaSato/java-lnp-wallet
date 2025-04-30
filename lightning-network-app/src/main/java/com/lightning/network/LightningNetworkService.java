package com.lightning.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lightning.db.DatabaseManager;
import com.lightning.model.Invoice;
import com.lightning.model.LightningInfo;
import com.lightning.model.Payment;
import com.lightning.model.WalletBalance;
import okhttp3.*;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for interacting with the Lightning Network node using REST API
 */
public class LightningNetworkService {
    private static final Logger LOGGER = Logger.getLogger(LightningNetworkService.class.getName());
    private static final String CONFIG_FILE = "lightning-config.properties";
    private static final String USER_CONFIG_DIR = ".lightning-wallet";
    private static final String USER_CONFIG_FILE = "lightning-config.properties";
    
    private OkHttpClient client;
    private String baseUrl;
    private final Gson gson;
    private boolean useHttps = true;
    private Properties configProps;
    
    /**
     * Initialize the Lightning Network service
     */
    public LightningNetworkService() {
        gson = new Gson();
        configProps = loadConfig();
        
        initializeConnection();
    }
    
    /**
     * Initialize or reinitialize the connection with current properties
     */
    public void initializeConnection() {
        // Use Docker-friendly connection settings by default (for development)
        String host = configProps.getProperty("host", NetworkConstants.DEFAULT_LIGHTNING_HOST);
        String port = configProps.getProperty("port", String.valueOf(NetworkConstants.DEFAULT_LIGHTNING_REST_PORT));
        String tlsCertPath = configProps.getProperty("tls.cert.path", "");
        
        // Try HTTPS first
        useHttps = true;
        baseUrl = NetworkConstants.getLightningRestUrl(host, Integer.parseInt(port), true);
        
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        
        // Configure TLS for secure communication with the Lightning node
        if (!configureTLS(builder, tlsCertPath)) {
            // If TLS configuration fails, try HTTP instead
            useHttps = false;
            baseUrl = NetworkConstants.getLightningRestUrl(host, Integer.parseInt(port), false);
            LOGGER.warning("SSL configuration failed. Falling back to HTTP (insecure) connection.");
        }
        
        client = builder.build();
        LOGGER.info("Lightning Network service initialized with URL: " + baseUrl);
    }
    
    /**
     * Configure TLS for secure communication with the Lightning node
     * @return true if configuration was successful, false otherwise
     */
    private boolean configureTLS(OkHttpClient.Builder builder, String certPath) {
        try {
            if (certPath == null || certPath.isEmpty()) {
                // If no cert path provided, try to find the certificate file in common locations
                String homeDir = System.getProperty("user.home");
                List<String> possiblePaths = new ArrayList<>();
                
                // Add common cert paths
                possiblePaths.add(Paths.get(homeDir, "Developments", "java-dev", "java-lnp-wallet", 
                        "bitcoin-lightning-dev", "data", "lightning", "tls.cert").toString());
                possiblePaths.add(Paths.get(homeDir, ".lnd", "tls.cert").toString());
                possiblePaths.add(Paths.get(homeDir, ".lightning", "tls.cert").toString());
                
                // Try each path until we find a valid certificate
                for (String path : possiblePaths) {
                    if (Files.exists(Path.of(path))) {
                        certPath = path;
                        LOGGER.info("Found TLS certificate at: " + certPath);
                        configProps.setProperty("tls.cert.path", certPath);
                        saveConfig(configProps);
                        break;
                    }
                }
            }
            
            // Always use trust all certificates for Docker development environment
            // This is needed because the certificate in Docker may not match the hostname
            LOGGER.info("Using development-mode TLS configuration with all-trusting trust manager");
            
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }
                        
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }
                        
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };
            
            // Install the all-trusting trust manager with supported protocols
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                   .hostnameVerifier((hostname, session) -> true);
            
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to set up TLS configuration", e);
            return false;
        }
    }
    
    /**
     * Test the connection to the Lightning node using multiple endpoints
     * @return true if connection is successful, false otherwise
     */
    public boolean testConnection() {
        try {
            // First try current baseUrl
            Request request = new Request.Builder()
                    .url(baseUrl + "/getinfo")
                    .build();
            
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                LOGGER.info("Successfully connected to Lightning node using: " + baseUrl);
                return true;
            }
            
            // If that fails, try standard endpoints
            
            // 1. Try Lightning REST API
            if (!baseUrl.equals(NetworkConstants.LIGHTNING_REST_API_URL + "/v1")) {
                String host = configProps.getProperty("host", NetworkConstants.DEFAULT_LIGHTNING_HOST);
                baseUrl = NetworkConstants.LIGHTNING_REST_API_URL + "/v1";
                useHttps = true;
                
                // Reconfigure client for HTTPS
                OkHttpClient.Builder builder = new OkHttpClient.Builder()
                        .connectTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                        .readTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                        .writeTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS);
                
                configureTLS(builder, configProps.getProperty("tls.cert.path", ""));
                client = builder.build();
                
                request = new Request.Builder()
                        .url(baseUrl + "/getinfo")
                        .build();
                
                try {
                    response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        LOGGER.info("Successfully connected to Lightning node using standard REST API: " + baseUrl);
                        
                        // Update config with working connection
                        configProps.setProperty("port", String.valueOf(NetworkConstants.DEFAULT_LIGHTNING_REST_PORT));
                        saveConfig(configProps);
                        
                        return true;
                    }
                } catch (Exception e) {
                    LOGGER.warning("Failed to connect using standard REST API: " + e.getMessage());
                }
            }
            
            // 2. Try Lightning RPC over HTTP
            baseUrl = NetworkConstants.LIGHTNING_RPC_URL + "/v1";
            useHttps = false;
            
            // Reconfigure client for HTTP
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS);
            
            client = builder.build();
            
            request = new Request.Builder()
                    .url(baseUrl + "/getinfo")
                    .build();
            
            try {
                response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    LOGGER.info("Successfully connected to Lightning node using RPC: " + baseUrl);
                    
                    // Update config with working connection
                    configProps.setProperty("port", String.valueOf(NetworkConstants.DEFAULT_LIGHTNING_RPC_PORT));
                    configProps.setProperty("useHttps", "false");
                    saveConfig(configProps);
                    
                    return true;
                }
            } catch (Exception e) {
                LOGGER.warning("Failed to connect using standard RPC: " + e.getMessage());
            }
            
            LOGGER.severe("Failed to connect to Lightning node using any endpoint");
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error testing connection to Lightning node", e);
            return false;
        }
    }
    
    /**
     * Load configuration from properties file
     */
    private Properties loadConfig() {
        Properties props = new Properties();
        
        // Default props if file doesn't exist
        props.setProperty("host", "localhost");
        props.setProperty("port", "8080");
        
        // For development, try to find the certificate from the docker setup
        String homeDir = System.getProperty("user.home");
        String dockerCertPath = Paths.get(homeDir, "Developments", "java-dev", "java-lnp-wallet", 
                "bitcoin-lightning-dev", "data", "lightning", "tls.cert").toString();
        
        if (Files.exists(Path.of(dockerCertPath))) {
            props.setProperty("tls.cert.path", dockerCertPath);
        }
        
        // First try to load from user home directory
        Path userConfigPath = Paths.get(System.getProperty("user.home"), USER_CONFIG_DIR, USER_CONFIG_FILE);
        if (Files.exists(userConfigPath)) {
            try (InputStream input = Files.newInputStream(userConfigPath)) {
                props.load(input);
                LOGGER.info("Loaded configuration from user config: " + userConfigPath);
                return props;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error loading user config file: " + userConfigPath, e);
            }
        }
        
        // If user config doesn't exist, try to load from bundled resource file
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                props.load(input);
                LOGGER.info("Loaded configuration from bundled resource: " + CONFIG_FILE);
            } else {
                LOGGER.warning("Could not find config file: " + CONFIG_FILE + ", using defaults");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error loading config file", e);
        }
        
        return props;
    }
    
    /**
     * Save configuration to properties file in user's home directory
     */
    public void saveConfig(Properties props) {
        // Create user config directory if it doesn't exist
        Path userConfigDir = Paths.get(System.getProperty("user.home"), USER_CONFIG_DIR);
        Path userConfigPath = userConfigDir.resolve(USER_CONFIG_FILE);
        
        try {
            if (!Files.exists(userConfigDir)) {
                Files.createDirectories(userConfigDir);
                LOGGER.info("Created user config directory: " + userConfigDir);
            }
            
            try (var output = Files.newOutputStream(userConfigPath)) {
                props.store(output, "Lightning Network Wallet Configuration");
                LOGGER.info("Saved configuration to: " + userConfigPath);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save settings to: " + userConfigPath, e);
        }
    }
    
    /**
     * Get information about the Lightning Network node
     */
    public LightningInfo getInfo() throws IOException {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/getinfo")
                    .build();
            
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                if (useHttps) {
                    // If HTTPS fails, try HTTP
                    LOGGER.warning("HTTPS request failed. Trying HTTP...");
                    useHttps = false;
                    String host = configProps.getProperty("host", "localhost");
                    String port = configProps.getProperty("port", "8080");
                    baseUrl = String.format("http://%s:%s/v1", host, port);
                    
                    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS);
                    
                    client = builder.build();
                    
                    // Try again with HTTP
                    request = new Request.Builder()
                            .url(baseUrl + "/getinfo")
                            .build();
                    
                    response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        throw new IOException("Failed to get node info: " + response);
                    }
                } else {
                    throw new IOException("Failed to get node info: " + response);
                }
            }
            
            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            
            LightningInfo info = new LightningInfo();
            info.setIdentityPubkey(json.get("identity_pubkey").getAsString());
            info.setAlias(json.get("alias").getAsString());
            info.setNumActiveChannels(json.get("num_active_channels").getAsInt());
            info.setNumPendingChannels(json.get("num_pending_channels").getAsInt());
            info.setNumPeers(json.get("num_peers").getAsInt());
            info.setBlockHeight(json.get("block_height").getAsInt());
            info.setSyncedToChain(json.get("synced_to_chain").getAsBoolean());
            
            return info;
        } catch (Exception e) {
            // If we're using HTTPS and get an error, try HTTP
            if (useHttps) {
                LOGGER.warning("HTTPS request failed with error. Trying HTTP...");
                useHttps = false;
                String host = configProps.getProperty("host", "localhost");
                String port = configProps.getProperty("port", "8080");
                baseUrl = String.format("http://%s:%s/v1", host, port);
                
                OkHttpClient.Builder builder = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS);
                
                client = builder.build();
                
                try {
                    // Try again with HTTP
                    Request request = new Request.Builder()
                            .url(baseUrl + "/getinfo")
                            .build();
                    
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        throw new IOException("Failed to get node info: " + response);
                    }
                    
                    JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                    
                    LightningInfo info = new LightningInfo();
                    info.setIdentityPubkey(json.get("identity_pubkey").getAsString());
                    info.setAlias(json.get("alias").getAsString());
                    info.setNumActiveChannels(json.get("num_active_channels").getAsInt());
                    info.setNumPendingChannels(json.get("num_pending_channels").getAsInt());
                    info.setNumPeers(json.get("num_peers").getAsInt());
                    info.setBlockHeight(json.get("block_height").getAsInt());
                    info.setSyncedToChain(json.get("synced_to_chain").getAsBoolean());
                    
                    return info;
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Failed to get node info via HTTP", ex);
                    throw new IOException("Failed to get node info: " + ex.getMessage(), e);
                }
            }
            throw new IOException("Failed to get node info: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get the wallet balance with fallback to local cache if network fails
     */
    public WalletBalance getWalletBalance() throws IOException {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/balance/blockchain")
                    .build();
            
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get wallet balance: " + response);
            }
            
            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            
            WalletBalance balance = new WalletBalance();
            balance.setTotalBalance(json.get("total_balance").getAsLong());
            balance.setConfirmedBalance(json.get("confirmed_balance").getAsLong());
            balance.setUnconfirmedBalance(json.get("unconfirmed_balance").getAsLong());
            
            // Cache the balance for offline mode
            saveBalanceToCache(balance);
            
            return balance;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting wallet balance from network, trying local cache", e);
            // Try to get last known balance from local cache
            WalletBalance cachedBalance = getBalanceFromCache();
            if (cachedBalance != null) {
                return cachedBalance;
            }
            
            // If no cached balance, return empty balance 
            LOGGER.log(Level.WARNING, "No cached balance available, returning empty balance");
            return new WalletBalance();
        }
    }
    
    /**
     * Save balance to local cache for offline mode
     */
    private void saveBalanceToCache(WalletBalance balance) {
        try {
            // Create path for cache if it doesn't exist
            Path cacheDir = Paths.get(System.getProperty("user.home"), USER_CONFIG_DIR, "cache");
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
            
            // Save balance to JSON file
            Path balanceCachePath = cacheDir.resolve("balance-cache.json");
            JsonObject json = new JsonObject();
            json.addProperty("total_balance", balance.getTotalBalance());
            json.addProperty("confirmed_balance", balance.getConfirmedBalance());
            json.addProperty("unconfirmed_balance", balance.getUnconfirmedBalance());
            json.addProperty("locked_balance", balance.getLockedBalance());
            json.addProperty("cached_at", System.currentTimeMillis());
            
            Files.writeString(balanceCachePath, gson.toJson(json));
            LOGGER.info("Saved balance to cache: " + balanceCachePath);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save balance to cache", e);
        }
    }
    
    /**
     * Get balance from local cache for offline mode
     */
    private WalletBalance getBalanceFromCache() {
        try {
            Path cacheDir = Paths.get(System.getProperty("user.home"), USER_CONFIG_DIR, "cache");
            Path balanceCachePath = cacheDir.resolve("balance-cache.json");
            
            if (Files.exists(balanceCachePath)) {
                String jsonStr = Files.readString(balanceCachePath);
                JsonObject json = gson.fromJson(jsonStr, JsonObject.class);
                
                WalletBalance balance = new WalletBalance();
                balance.setTotalBalance(json.get("total_balance").getAsLong());
                balance.setConfirmedBalance(json.get("confirmed_balance").getAsLong());
                balance.setUnconfirmedBalance(json.get("unconfirmed_balance").getAsLong());
                
                if (json.has("locked_balance")) {
                    balance.setLockedBalance(json.get("locked_balance").getAsLong());
                }
                
                LOGGER.info("Loaded balance from cache: " + balanceCachePath);
                return balance;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load balance from cache", e);
        }
        return null;
    }
    
    /**
     * Create a new invoice
     */
    public Invoice createInvoice(long amountSats, String memo) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("value", amountSats);
        payload.addProperty("memo", memo);
        
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), gson.toJson(payload));
        
        Request request = new Request.Builder()
                .url(baseUrl + "/invoices")
                .post(body)
                .build();
        
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to create invoice: " + response);
        }
        
        JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
        
        Invoice invoice = new Invoice();
        invoice.setPaymentRequest(json.get("payment_request").getAsString());
        invoice.setRHash(json.get("r_hash").getAsString());
        invoice.setAddIndex(json.get("add_index").getAsLong());
        invoice.setMemo(memo);
        invoice.setAmountSats(amountSats);
        invoice.setSettled(false);
        
        return invoice;
    }
    
    /**
     * Get all invoices
     */
    public List<Invoice> listInvoices() throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/invoices")
                .build();
        
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to list invoices: " + response);
        }
        
        JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
        
        List<Invoice> invoices = new ArrayList<>();
        json.getAsJsonArray("invoices").forEach(element -> {
            JsonObject obj = element.getAsJsonObject();
            
            Invoice invoice = new Invoice();
            invoice.setPaymentRequest(obj.get("payment_request").getAsString());
            invoice.setRHash(obj.get("r_hash").getAsString());
            invoice.setMemo(obj.get("memo").getAsString());
            invoice.setAmountSats(obj.get("value").getAsLong());
            invoice.setSettled(obj.get("settled").getAsBoolean());
            invoice.setCreationDate(obj.get("creation_date").getAsLong());
            
            if (invoice.isSettled() && obj.has("settle_date")) {
                invoice.setSettleDate(obj.get("settle_date").getAsLong());
            }
            
            invoices.add(invoice);
        });
        
        return invoices;
    }
    
    /**
     * Pay a Lightning invoice
     */
    public Payment payInvoice(String paymentRequest) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("payment_request", paymentRequest);
        
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), gson.toJson(payload));
        
        Request request = new Request.Builder()
                .url(baseUrl + "/channels/transactions")
                .post(body)
                .build();
        
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to pay invoice: " + response);
        }
        
        JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
        
        Payment payment = new Payment();
        payment.setPaymentHash(json.get("payment_hash").getAsString());
        payment.setPaymentPreimage(json.get("payment_preimage").getAsString());
        payment.setPaymentRoute(json.get("payment_route").toString());
        payment.setValueSat(json.getAsJsonObject("payment_route").get("total_amt").getAsLong());
        payment.setFeeSat(json.getAsJsonObject("payment_route").get("total_fees").getAsLong());
        
        return payment;
    }
    
    /**
     * Generate a new Bitcoin address for funding the wallet
     */
    public String getNewAddress() throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "p2wkh"); // Use native segwit
        
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), gson.toJson(payload));
        
        Request request = new Request.Builder()
                .url(baseUrl + "/newaddress")
                .post(body)
                .build();
        
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to get new address: " + response);
        }
        
        JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
        return json.get("address").getAsString();
    }
    
    /**
     * Get detailed node information
     */
    public LightningInfo getNodeInfo() throws IOException {
        return getInfo();
    }
    
    /**
     * Decode a payment request
     */
    public Payment decodePaymentRequest(String paymentRequest) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("pay_req", paymentRequest);
        
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), gson.toJson(payload));
        
        Request request = new Request.Builder()
                .url(baseUrl + "/payreq/" + paymentRequest)
                .build();
        
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to decode payment request: " + response);
        }
        
        JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
        
        Payment payment = new Payment();
        payment.setDestination(json.get("destination").getAsString());
        payment.setPaymentHash(json.get("payment_hash").getAsString());
        payment.setNumSatoshis(json.get("num_satoshis").getAsLong());
        if (json.has("description")) {
            payment.setDescription(json.get("description").getAsString());
        }
        if (json.has("timestamp")) {
            payment.setTimestamp(json.get("timestamp").getAsLong());
        }
        
        return payment;
    }
    
    /**
     * Send a payment using payment request
     */
    public Boolean sendPayment(String paymentRequest) throws IOException {
        try {
            // Call the payInvoice method which does the actual payment
            Payment payment = payInvoice(paymentRequest);
            // If we get here without an exception, the payment was successful
            return payment != null;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to send payment", e);
            return false;
        }
    }
    
    /**
     * Establish connection to Lightning Network node
     */
    public boolean connect() throws IOException {
        try {
            // First test the current connection configuration
            if (testConnection()) {
                return true;
            }
            
            // If initial test fails, reinitialize connection with defaults
            initializeConnection();
            return testConnection();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to connect to Lightning node", e);
            return false;
        }
    }
    
    /**
     * Apply settings from configuration properties
     */
    public void applySettings(Properties props) {
        // Save settings to user home directory
        saveConfig(props);
        this.configProps = props;
        
        // Reinitialize connection with new settings
        initializeConnection();
        
        LOGGER.info("New settings applied");
    }
    
    /**
     * Shutdown the Lightning Network service
     */
    public void shutdown() {
        // Close database connection
        DatabaseManager.getInstance().close();
        
        if (client != null && client.dispatcher() != null && 
                client.dispatcher().executorService() != null) {
            client.dispatcher().executorService().shutdown();
        }
        if (client != null && client.connectionPool() != null) {
            client.connectionPool().evictAll();
        }
    }
    
    /**
     * Save an invoice to the local database
     */
    private void saveInvoiceToDatabase(Invoice invoice) {
        DatabaseManager.getInstance().saveInvoice(invoice);
    }
    
    /**
     * Save a payment to the local database
     */
    private void savePaymentToDatabase(Payment payment) {
        DatabaseManager.getInstance().savePayment(payment);
    }
    
    /**
     * Get invoices from the local database
     */
    public List<Invoice> getLocalInvoices() {
        return DatabaseManager.getInstance().getAllInvoices();
    }
    
    /**
     * Create a new invoice and save it to the database
     */
    public Invoice createInvoiceAndSave(long amountSats, String memo) throws IOException {
        Invoice invoice = createInvoice(amountSats, memo);
        // Save to database
        saveInvoiceToDatabase(invoice);
        return invoice;
    }
    
    /**
     * Pay an invoice and save the payment to the database
     */
    public Payment payInvoiceAndSave(String paymentRequest) throws IOException {
        Payment payment = payInvoice(paymentRequest);
        // Save to database
        savePaymentToDatabase(payment);
        return payment;
    }
    
    /**
     * Check if a Lightning node is running at the specified host and port
     * @param host The host address to check
     * @param port The port to check
     * @param timeout Connection timeout in milliseconds
     * @return true if a connection can be established, false otherwise
     */
    public boolean isNodeRunning(String host, int port, int timeout) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeout);
            socket.close();
            LOGGER.info("Lightning node is reachable at " + host + ":" + port);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lightning node is not reachable at " + host + ":" + port, e);
            return false;
        }
    }
    
    /**
     * Discover Lightning node port by testing common ports
     * @param host The host to check
     * @return The port number if found, -1 otherwise
     */
    public int discoverNodePort(String host) {
        LOGGER.info("Attempting to discover Lightning node port...");
        // Common ports for different Lightning implementations
        int[] commonPorts = {
            8080,  // Default REST API port
            10009, // Default gRPC port (lnd)
            9735,  // Default Lightning Network p2p port
            3000,  // Default REST API port (c-lightning REST plugin)
            8181   // Alternative REST port
        };
        
        for (int port : commonPorts) {
            if (isNodeRunning(host, port, 500)) {
                LOGGER.info("Found Lightning node running on port " + port);
                return port;
            }
        }
        
        LOGGER.warning("Could not discover Lightning node port automatically");
        return -1;
    }
    
    /**
     * Run connection diagnostics including all standard endpoints
     */
    public String connectionDiagnostics() {
        StringBuilder diagnostics = new StringBuilder();
        String host = configProps.getProperty("host", NetworkConstants.DEFAULT_LIGHTNING_HOST);
        int port = Integer.parseInt(configProps.getProperty("port", String.valueOf(NetworkConstants.DEFAULT_LIGHTNING_REST_PORT)));
        
        diagnostics.append("Connection Diagnostics Report:\n");
        diagnostics.append("------------------------\n");
        diagnostics.append("Time: ").append(java.time.LocalDateTime.now()).append("\n");
        diagnostics.append("Configured Host: ").append(host).append("\n");
        diagnostics.append("Configured Port: ").append(port).append("\n\n");
        
        // Check standard endpoints
        diagnostics.append("Testing standard endpoints:\n");
        
        // Bitcoin RPC
        boolean bitcoinRpcAvailable = isNodeRunning(
            NetworkConstants.DEFAULT_BITCOIN_HOST, 
            NetworkConstants.DEFAULT_BITCOIN_PORT, 
            2000
        );
        diagnostics.append("Bitcoin RPC (").append(NetworkConstants.BITCOIN_RPC_URL).append("): ")
                  .append(bitcoinRpcAvailable ? "AVAILABLE" : "NOT AVAILABLE").append("\n");
        
        // Lightning RPC
        boolean lightningRpcAvailable = isNodeRunning(
            NetworkConstants.DEFAULT_LIGHTNING_HOST, 
            NetworkConstants.DEFAULT_LIGHTNING_RPC_PORT, 
            2000
        );
        diagnostics.append("Lightning RPC (").append(NetworkConstants.LIGHTNING_RPC_URL).append("): ")
                  .append(lightningRpcAvailable ? "AVAILABLE" : "NOT AVAILABLE").append("\n");
        
        // Lightning REST API
        boolean lightningRestAvailable = isNodeRunning(
            NetworkConstants.DEFAULT_LIGHTNING_HOST, 
            NetworkConstants.DEFAULT_LIGHTNING_REST_PORT, 
            2000
        );
        diagnostics.append("Lightning REST API (").append(NetworkConstants.LIGHTNING_REST_API_URL).append("): ")
                  .append(lightningRestAvailable ? "AVAILABLE" : "NOT AVAILABLE").append("\n\n");
        
        // Basic connectivity check with configured endpoint
        boolean basicConnectivity = isNodeRunning(host, port, 2000);
        diagnostics.append("Configured Endpoint Connectivity: ").append(basicConnectivity ? "SUCCESS" : "FAILED").append("\n");
        
        // Check Bitcoin node status
        diagnostics.append("Checking Bitcoin Node...\n");
        boolean bitcoinNodeRunning = false;
        try {
            // Try to check if Bitcoin node is running on default port
            int bitcoinPort = 8332; // Default for Bitcoin Core RPC
            bitcoinNodeRunning = isNodeRunning(NetworkConstants.DEFAULT_BITCOIN_HOST, bitcoinPort, 1000);
            diagnostics.append("Bitcoin Node Reachable: ").append(bitcoinNodeRunning ? "YES" : "NO").append("\n");
        } catch (Exception e) {
            diagnostics.append("Error checking Bitcoin node: ").append(e.getMessage()).append("\n");
        }
        
        // If basic connectivity fails, try to discover port
        if (!basicConnectivity) {
            diagnostics.append("Attempting port discovery...\n");
            int discoveredPort = discoverNodePort(host);
            if (discoveredPort > 0) {
                diagnostics.append("Found alternative port: ").append(discoveredPort).append("\n");
                diagnostics.append("Recommendation: Update configuration to use port ").append(discoveredPort).append("\n");
            } else {
                diagnostics.append("No alternative ports found\n");
                
                // Check if the host itself is reachable
                try {
                    InetAddress address = InetAddress.getByName(host);
                    boolean reachable = address.isReachable(2000);
                    diagnostics.append("Host Reachable: ").append(reachable ? "YES" : "NO").append("\n");
                    
                    if (!reachable) {
                        diagnostics.append("Recommendations:\n");
                        diagnostics.append("1. Check if Lightning node is running\n");
                        diagnostics.append("2. Verify host address is correct\n");
                        diagnostics.append("3. Check network connectivity and firewall settings\n");
                    }
                } catch (Exception e) {
                    diagnostics.append("Host lookup failed: ").append(e.getMessage()).append("\n");
                    diagnostics.append("Recommendation: Verify the host address is correct\n");
                }
            }
        } else {
            // Try API call if connectivity is successful
            try {
                LightningInfo info = getInfo();
                diagnostics.append("API Test: SUCCESS\n");
                diagnostics.append("Node Alias: ").append(info.getAlias()).append("\n");
                diagnostics.append("Pubkey: ").append(info.getIdentityPubkey()).append("\n");
                diagnostics.append("Synced to Chain: ").append(info.isSyncedToChain() ? "YES" : "NO").append("\n");
                diagnostics.append("Block Height: ").append(info.getBlockHeight()).append("\n");
            } catch (Exception e) {
                diagnostics.append("API Test: FAILED\n");
                diagnostics.append("Error: ").append(e.getMessage()).append("\n");
                
                // Check for sync issues in the error message
                if (e.getMessage() != null && 
                    (e.getMessage().contains("Connection reset") || 
                     e.getMessage().contains("sync") ||
                     e.getMessage().contains("height") ||
                     e.getMessage().contains("Block height out of range"))) {
                    
                    diagnostics.append("\nPossible Synchronization Issue Detected:\n");
                    diagnostics.append("The Lightning node appears to be running but might be synchronizing with the Bitcoin blockchain.\n");
                    diagnostics.append("This process can take some time and may cause connection resets or API failures.\n");
                    diagnostics.append("Recommendation: Wait for the node to fully synchronize and try again later.\n");
                    
                    // Check logs if possible
                    try {
                        String homeDir = System.getProperty("user.home");
                        Path logPath = Path.of(homeDir, "Developments", "java-dev", "java-lnp-wallet", 
                                "bitcoin-lightning-dev", "logs", "lightning", "lightning.log");
                        
                        if (Files.exists(logPath)) {
                            diagnostics.append("\nChecking Lightning node logs...\n");
                            List<String> logLines = Files.readAllLines(logPath);
                            
                            // Get the last few lines that might contain sync info
                            int startIndex = Math.max(0, logLines.size() - 10);
                            for (int i = startIndex; i < logLines.size(); i++) {
                                String line = logLines.get(i);
                                if (line.contains("sync") || line.contains("chain") || line.contains("block")) {
                                    diagnostics.append("Log: ").append(line).append("\n");
                                }
                            }
                        }
                    } catch (Exception logEx) {
                        // Ignore log reading errors
                    }
                } else {
                    diagnostics.append("Recommendation: Lightning node may be running but API access is restricted\n");
                }
            }
        }
        
        return diagnostics.toString();
    }
    
    /**
     * Auto-fix connection by trying all standard endpoints
     */
    public boolean autoFixConnection() {
        LOGGER.info("Attempting to auto-fix connection to Lightning node...");
        
        // Check standard endpoints
        
        // 1. Try Lightning REST API
        if (isNodeRunning(NetworkConstants.DEFAULT_LIGHTNING_HOST, NetworkConstants.DEFAULT_LIGHTNING_REST_PORT, 2000)) {
            LOGGER.info("Found Lightning REST API endpoint available");
            
            // Update config
            configProps.setProperty("host", NetworkConstants.DEFAULT_LIGHTNING_HOST);
            configProps.setProperty("port", String.valueOf(NetworkConstants.DEFAULT_LIGHTNING_REST_PORT));
            configProps.setProperty("useHttps", "true");
            saveConfig(configProps);
            
            // Reinitialize connection
            initializeConnection();
            
            // Test connection
            if (testConnection()) {
                LOGGER.info("Connection fixed using Lightning REST API endpoint");
                return true;
            }
        }
        
        // 2. Try Lightning RPC
        if (isNodeRunning(NetworkConstants.DEFAULT_LIGHTNING_HOST, NetworkConstants.DEFAULT_LIGHTNING_RPC_PORT, 2000)) {
            LOGGER.info("Found Lightning RPC endpoint available");
            
            // Update config
            configProps.setProperty("host", NetworkConstants.DEFAULT_LIGHTNING_HOST);
            configProps.setProperty("port", String.valueOf(NetworkConstants.DEFAULT_LIGHTNING_RPC_PORT));
            configProps.setProperty("useHttps", "false");
            saveConfig(configProps);
            
            // Reinitialize connection
            initializeConnection();
            
            // Test connection
            if (testConnection()) {
                LOGGER.info("Connection fixed using Lightning RPC endpoint");
                return true;
            }
        }
        
        // Original auto-fix logic
        String host = configProps.getProperty("host", "localhost");
        String port = configProps.getProperty("port", "8080");
        
        LOGGER.info("Trying common localhost addresses...");
        String[] commonAddresses = {"127.0.0.1", "0.0.0.0"};
        for (String address : commonAddresses) {
            if (isNodeRunning(address, Integer.parseInt(port), 1000)) {
                // Update config with new host
                configProps.setProperty("host", address);
                saveConfig(configProps);
                
                // Reinitialize connection
                initializeConnection();
                
                // Test connection with new settings
                if (testConnection()) {
                    LOGGER.info("Connection fixed with new host: " + address);
                    return true;
                }
            }
        }
        
        LOGGER.warning("Could not auto-fix connection to Lightning node");
        return false;
    }
    
    /**
     * Check if gRPC proxy is available
     * @return true if gRPC proxy is available, false otherwise
     */
    public boolean isGrpcProxyAvailable() {
        String host = configProps.getProperty("grpc.host", "127.0.0.1");
        int port = Integer.parseInt(configProps.getProperty("grpc.port", "8080"));
        
        LOGGER.info("Checking if gRPC proxy is available at " + host + ":" + port);
        
        // Check if the gRPC proxy is running at the specified host and port
        boolean isAvailable = isNodeRunning(host, port, 1000);
        
        if (isAvailable) {
            LOGGER.info("gRPC proxy is available");
        } else {
            LOGGER.info("gRPC proxy is not available");
        }
        
        return isAvailable;
    }
    
    /**
     * List invoices using gRPC proxy
     */
    public List<Invoice> listInvoicesViaGrpc() throws IOException {
        // For now, we'll use the REST API implementation
        // In a full implementation, you would connect to the gRPC server and call the appropriate methods
        LOGGER.info("Using gRPC proxy for listing invoices (via REST API bridge for now)");
        return listInvoices();
    }
}