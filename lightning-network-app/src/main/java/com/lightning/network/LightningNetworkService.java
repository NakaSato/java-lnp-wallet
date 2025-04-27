package com.lightning.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lightning.model.Invoice;
import com.lightning.model.LightningInfo;
import com.lightning.model.Payment;
import com.lightning.model.WalletBalance;
import okhttp3.*;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    
    private final OkHttpClient client;
    private final String baseUrl;
    private final Gson gson;
    
    /**
     * Initialize the Lightning Network service
     */
    public LightningNetworkService() {
        gson = new Gson();
        Properties props = loadConfig();
        
        String host = props.getProperty("host", "localhost");
        String port = props.getProperty("port", "8080");
        String tlsCertPath = props.getProperty("tls.cert.path", "");
        
        baseUrl = String.format("https://%s:%s/v1", host, port);
        
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);
        
        // Configure TLS for secure communication with the Lightning node
        configureTLS(builder, tlsCertPath);
        
        client = builder.build();
        LOGGER.info("Lightning Network service initialized with URL: " + baseUrl);
    }
    
    /**
     * Configure TLS for secure communication with the Lightning node
     */
    private void configureTLS(OkHttpClient.Builder builder, String certPath) {
        if (certPath.isEmpty()) {
            // If no cert path provided, just trust all certificates (for development only)
            try {
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
                
                // Install the all-trusting trust manager
                final SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                
                // Create an ssl socket factory with our all-trusting manager
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                
                builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
                builder.hostnameVerifier((hostname, session) -> true);
                
                LOGGER.warning("Using insecure TLS configuration (trust all certificates). Do not use in production!");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to set up TLS trust manager", e);
            }
        } else {
            // Use provided certificate
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                Certificate certificate;
                
                try (InputStream certificateInputStream = new FileInputStream(certPath)) {
                    certificate = certificateFactory.generateCertificate(certificateInputStream);
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
                
                // Create an SSLContext that uses our TrustManager
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagers, null);
                
                builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
                
                LOGGER.info("TLS configured with certificate: " + certPath);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to load TLS certificate", e);
            }
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
        
        // Try to load from config file, overriding defaults if exists
        try {
            InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE);
            if (input != null) {
                props.load(input);
                input.close();
            } else {
                LOGGER.warning("Could not find config file: " + CONFIG_FILE + ", using defaults");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error loading config file", e);
        }
        
        return props;
    }
    
    /**
     * Get information about the Lightning Network node
     */
    public LightningInfo getInfo() throws IOException {
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
     * Shutdown the Lightning Network service
     */
    public void shutdown() {
        if (client != null && client.dispatcher() != null && 
                client.dispatcher().executorService() != null) {
            client.dispatcher().executorService().shutdown();
        }
        if (client != null && client.connectionPool() != null) {
            client.connectionPool().evictAll();
        }
    }
}