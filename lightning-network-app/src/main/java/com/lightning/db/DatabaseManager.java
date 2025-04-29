package com.lightning.db;

import java.io.File;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.lightning.model.Invoice;
import com.lightning.model.Payment;
import com.lightning.wallet.Transaction;

/**
 * Utility class for managing SQLite database operations
 */
public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final String DB_NAME = "lightning_wallet.db";
    private static final String DB_PATH = getDbPath();
    private static final String URL = "jdbc:sqlite:" + DB_PATH;
    
    private static DatabaseManager instance;
    private Connection connection;
    
    /**
     * Get the database file path
     */
    private static String getDbPath() {
        String userHome = System.getProperty("user.home");
        String dbDir = Paths.get(userHome, ".lightning-wallet").toString();
        File dir = new File(dbDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return Paths.get(dbDir, DB_NAME).toString();
    }
    
    /**
     * Private constructor for singleton pattern
     */
    private DatabaseManager() {
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            
            // Create database connection
            connection = DriverManager.getConnection(URL);
            LOGGER.info("Database connection established: " + URL);
            
            // Initialize database tables
            initDatabase();
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "SQLite JDBC driver not found", e);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to connect to database", e);
        }
    }
    
    /**
     * Get the singleton instance of the DatabaseManager
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    /**
     * Initialize database tables if they don't exist
     */
    private void initDatabase() {
        try (Statement stmt = connection.createStatement()) {
            // Create transactions table
            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "transaction_id TEXT NOT NULL," +
                    "type TEXT NOT NULL," +
                    "amount REAL NOT NULL," +
                    "status TEXT NOT NULL," +
                    "description TEXT," +
                    "timestamp INTEGER NOT NULL," +
                    "payment_hash TEXT," +
                    "fee_amount REAL," +
                    "memo TEXT)");
            
            // Create invoices table
            stmt.execute("CREATE TABLE IF NOT EXISTS invoices (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "payment_request TEXT NOT NULL," +
                    "r_hash TEXT NOT NULL," +
                    "memo TEXT," +
                    "amount_sats INTEGER NOT NULL," +
                    "settled INTEGER NOT NULL," +
                    "creation_date INTEGER NOT NULL," +
                    "settle_date INTEGER)");
            
            // Create payments table
            stmt.execute("CREATE TABLE IF NOT EXISTS payments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "payment_hash TEXT NOT NULL," +
                    "payment_preimage TEXT," +
                    "value_sat INTEGER NOT NULL," +
                    "fee_sat INTEGER NOT NULL," +
                    "status TEXT NOT NULL," +
                    "timestamp INTEGER NOT NULL," +
                    "destination TEXT," +
                    "description TEXT)");
            
            // Create settings table
            stmt.execute("CREATE TABLE IF NOT EXISTS settings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "key TEXT UNIQUE NOT NULL," +
                    "value TEXT)");
            
            LOGGER.info("Database tables initialized");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database tables", e);
        }
    }
    
    /**
     * Save a transaction to the database
     */
    public void saveTransaction(Transaction transaction, String type, long timestamp) {
        String sql = "INSERT INTO transactions (transaction_id, type, amount, status, timestamp) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, transaction.getTransactionId());
            pstmt.setString(2, type);
            pstmt.setDouble(3, transaction.getAmount());
            pstmt.setString(4, transaction.getStatus());
            pstmt.setLong(5, timestamp);
            pstmt.executeUpdate();
            LOGGER.info("Transaction saved: " + transaction.getTransactionId());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save transaction", e);
        }
    }
    
    /**
     * Save an invoice to the database
     */
    public void saveInvoice(Invoice invoice) {
        String sql = "INSERT INTO invoices (payment_request, r_hash, memo, amount_sats, settled, creation_date, settle_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, invoice.getPaymentRequest());
            pstmt.setString(2, invoice.getRHash());
            pstmt.setString(3, invoice.getMemo());
            pstmt.setLong(4, invoice.getAmountSats());
            pstmt.setInt(5, invoice.isSettled() ? 1 : 0);
            pstmt.setLong(6, invoice.getCreationDate());
            pstmt.setLong(7, invoice.isSettled() ? invoice.getSettleDate() : 0);
            pstmt.executeUpdate();
            LOGGER.info("Invoice saved: " + invoice.getRHash());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save invoice", e);
        }
    }
    
    /**
     * Update an invoice's settled status
     */
    public void updateInvoiceSettled(String rHash, boolean settled, long settleDate) {
        String sql = "UPDATE invoices SET settled = ?, settle_date = ? WHERE r_hash = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, settled ? 1 : 0);
            pstmt.setLong(2, settleDate);
            pstmt.setString(3, rHash);
            pstmt.executeUpdate();
            LOGGER.info("Invoice status updated: " + rHash);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update invoice status", e);
        }
    }
    
    /**
     * Save a payment to the database
     */
    public void savePayment(Payment payment) {
        String sql = "INSERT INTO payments (payment_hash, payment_preimage, value_sat, fee_sat, status, timestamp, destination, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, payment.getPaymentHash());
            pstmt.setString(2, payment.getPaymentPreimage());
            pstmt.setLong(3, payment.getValueSat());
            pstmt.setLong(4, payment.getFeeSat());
            pstmt.setString(5, "Completed");
            pstmt.setLong(6, System.currentTimeMillis() / 1000);
            pstmt.setString(7, payment.getDestination());
            pstmt.setString(8, payment.getDescription());
            pstmt.executeUpdate();
            LOGGER.info("Payment saved: " + payment.getPaymentHash());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save payment", e);
        }
    }
    
    /**
     * Get all transactions from the database
     */
    public List<Transaction> getAllTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT transaction_id, amount, status FROM transactions ORDER BY timestamp DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String id = rs.getString("transaction_id");
                double amount = rs.getDouble("amount");
                String status = rs.getString("status");
                
                transactions.add(new Transaction(id, amount, status));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get transactions", e);
        }
        
        return transactions;
    }
    
    /**
     * Get all invoices from the database
     */
    public List<Invoice> getAllInvoices() {
        List<Invoice> invoices = new ArrayList<>();
        String sql = "SELECT payment_request, r_hash, memo, amount_sats, settled, creation_date, settle_date FROM invoices ORDER BY creation_date DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Invoice invoice = new Invoice();
                invoice.setPaymentRequest(rs.getString("payment_request"));
                invoice.setRHash(rs.getString("r_hash"));
                invoice.setMemo(rs.getString("memo"));
                invoice.setAmountSats(rs.getLong("amount_sats"));
                invoice.setSettled(rs.getInt("settled") == 1);
                invoice.setCreationDate(rs.getLong("creation_date"));
                
                if (invoice.isSettled()) {
                    invoice.setSettleDate(rs.getLong("settle_date"));
                }
                
                invoices.add(invoice);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get invoices", e);
        }
        
        return invoices;
    }
    
    /**
     * Save a setting to the database
     */
    public void saveSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
            LOGGER.info("Setting saved: " + key);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to save setting", e);
        }
    }
    
    /**
     * Get a setting from the database
     */
    public String getSetting(String key, String defaultValue) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get setting: " + key, e);
        }
        
        return defaultValue;
    }
    
    /**
     * Close the database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOGGER.info("Database connection closed");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to close database connection", e);
        }
    }
}