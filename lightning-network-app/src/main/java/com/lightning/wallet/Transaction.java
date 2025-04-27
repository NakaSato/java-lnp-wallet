package com.lightning.wallet;

public class Transaction {
    private String transactionId;
    private double amount;
    private String status;

    public Transaction(String transactionId, double amount) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.status = "Pending"; // Default status
    }
    
    public Transaction(String transactionId, double amount, String status) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.status = status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public double getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public void processTransaction() {
        // Simulate processing the transaction
        System.out.println("Processing transaction: " + transactionId);
        // Update status to completed after processing
        this.status = "Completed";
    }

    public boolean validateTransaction() {
        // Simulate validation logic
        return amount > 0; // Example validation: amount must be positive
    }
}