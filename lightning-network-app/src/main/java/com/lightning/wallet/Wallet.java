package com.lightning.wallet;

import java.util.ArrayList;
import java.util.List;

public class Wallet {
    private double balance;
    private List<Transaction> transactionHistory;

    public Wallet() {
        this.balance = 0.0;
        this.transactionHistory = new ArrayList<>();
    }

    public double getBalance() {
        return balance;
    }

    public void addFunds(double amount) {
        if (amount > 0) {
            balance += amount;
            transactionHistory.add(new Transaction("Deposit", amount, "Completed"));
        } else {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
    
    // Added method for test compatibility
    public void deposit(int amount) {
        addFunds(amount);
    }

    public void createTransaction(double amount) {
        if (amount > 0 && amount <= balance) {
            balance -= amount;
            transactionHistory.add(new Transaction("Payment", amount, "Pending"));
        } else {
            throw new IllegalArgumentException("Insufficient balance or invalid amount");
        }
    }
    
    // Added method for test compatibility
    public void withdraw(int amount) {
        if (amount > 0 && amount <= balance) {
            balance -= amount;
            transactionHistory.add(new Transaction("Withdrawal", amount, "Completed"));
        } else {
            throw new IllegalArgumentException("Insufficient balance");
        }
    }

    public List<Transaction> getTransactionHistory() {
        return transactionHistory;
    }
}