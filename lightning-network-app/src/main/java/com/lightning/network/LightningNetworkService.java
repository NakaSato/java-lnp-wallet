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
        String host = configProps.getProperty("host", "localhost");
        String port = configProps.getProperty("port", "8080");
        String tlsCertPath = configProps.getProperty("tls.cert.path", "");
        
        // Try HTTPS first
        useHttps = true;
        baseUrl = String.format("https://%s:%s/v1", host, port);
        
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        
        // Configure TLS for secure communication with the Lightning node
        if (!configureTLS(builder, tlsCertPath)) {
            // If TLS configuration fails, try HTTP instead
            useHttps = false;
            baseUrl = String.format("http://%s:%s/v1", host, port);
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
            
            if (certPath == null || certPath.isEmpty() || !Files.exists(Path.of(certPath))) {
                // If we still don't have a valid cert path, try to trust all certificates (for development only)
                LOGGER.warning("No valid TLS certificate found. Using insecure TLS configuration.");
                
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
                final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                
                // Create an ssl socket factory with our all-trusting manager
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                
                builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                       .hostnameVerifier((hostname, session) -> true);
                
                LOGGER.warning("Using insecure TLS configuration (trust all certificates). Do not use in production!");
            } else {
                // Use provided certificate
                try {
                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                    Certificate certificate;
                    
                    try (InputStream certificateInputStream = new FileInputStream(certPath)) {
                        certificate = certificateFactory.generateCertificate(certificateInputStream);
                        LOGGER.info("Loaded certificate from: " + certPath);
                    }
                    
                    // Create a KeyStore containing our trusted certificates
                    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                    keyStore.load(null, null);
                    keyStore.setCertificateEntry("lnd", certificate);
                    
                    // Create a TrustManager that trusts the CAs in our KeyStore
                    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init(keyStore);
                    TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                    
                    // Create an SSLContext that uses our TrustManager with TLS 1.2
                    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                    sslContext.init(null, trustManagers, null);
                    
                    builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0])
                           .hostnameVerifier((hostname, session) -> true); // Ignore hostname verification for localhost
                    
                    LOGGER.info("TLS configured with certificate: " + certPath);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to load TLS certificate from: " + certPath, e);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to set up TLS configuration", e);
            return false;
        }
    }
    
    /**
     * Test the connection to the Lightning node
     * @return true if connection is successful, false otherwise
     */
    public boolean testConnection() {
        try {
            // First try HTTPS
            Request request = new Request.Builder()
                    .url(baseUrl + "/getinfo")
                    .build();
            
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                LOGGER.info("Successfully connected to Lightning node using: " + baseUrl);
                return true;
            }
            
            // If HTTPS fails, try HTTP if we haven't already
            if (useHttps) {
                LOGGER.warning("HTTPS connection failed. Trying HTTP...");
                useHttps = false;
                String host = configProps.getProperty("host", "localhost");
                String port = configProps.getProperty("port", "8080");
                baseUrl = String.format("http://%s:%s/v1", host, port);
                
                OkHttpClient.Builder builder = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS);
                
                client = builder.build();
                
                request = new Request.Builder()
                        .url(baseUrl + "/getinfo")
                        .build();
                
                response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    LOGGER.info("Successfully connected to Lightning node using HTTP: " + baseUrl);
                    return true;
                }
            }
            
            LOGGER.severe("Failed to connect to Lightning node: " + response);
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error testing connection to Lightning node", e);
            if (useHttps) {
                // If HTTPS fails, try HTTP
                LOGGER.warning("HTTPS connection failed with error. Trying HTTP...");
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
                    Request request = new Request.Builder()
                            .url(baseUrl + "/getinfo")
                            .build();
                    
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        LOGGER.info("Successfully connected to Lightning node using HTTP: " + baseUrl);
                        return true;
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Error connecting via HTTP", ex);
                }
            }
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
     * Get the wallet balance
     */
    public WalletBalance getWalletBalance() throws IOException {
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
        
        return balance;
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
     * Advanced connection test with detailed diagnostics
     * @return A diagnostic message about the connection
     */
    public String connectionDiagnostics() {
        StringBuilder diagnostics = new StringBuilder();
        String host = configProps.getProperty("host", "localhost");
        int port = Integer.parseInt(configProps.getProperty("port", "8080"));
        
        diagnostics.append("Connection Diagnostics Report:\n");
        diagnostics.append("------------------------\n");
        diagnostics.append("Time: ").append(java.time.LocalDateTime.now()).append("\n");
        diagnostics.append("Configured Host: ").append(host).append("\n");
        diagnostics.append("Configured Port: ").append(port).append("\n");
        
        // Basic connectivity check
        boolean basicConnectivity = isNodeRunning(host, port, 2000);
        diagnostics.append("Basic Connectivity: ").append(basicConnectivity ? "SUCCESS" : "FAILED").append("\n");
        
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
            } catch (Exception e) {
                diagnostics.append("API Test: FAILED\n");
                diagnostics.append("Error: ").append(e.getMessage()).append("\n");
                diagnostics.append("Recommendation: Lightning node may be running but API access is restricted\n");
            }
        }
        
        return diagnostics.toString();
    }
    
    /**
     * Auto-fix connection issues by discovering the correct port and updating configuration
     * @return true if connection was fixed, false otherwise
     */
    public boolean autoFixConnection() {
        String host = configProps.getProperty("host", "localhost");
        int port = Integer.parseInt(configProps.getProperty("port", "8080"));
        
        LOGGER.info("Attempting to auto-fix connection to Lightning node...");
        
        // Check current config first
        if (isNodeRunning(host, port, 2000)) {
            LOGGER.info("Connection with current settings is working");
            return true;
        }
        
        // Try to discover port
        int discoveredPort = discoverNodePort(host);
        if (discoveredPort > 0 && discoveredPort != port) {