package com.lightning.wallet;

public class PaymentHandler {
    public void initiatePayment(String recipientAddress, double amount) {
        System.out.println("Initiating payment of " + amount + " to " + recipientAddress);
        // Logic to initiate payment goes here
    }

    public void handlePaymentConfirmation(String transactionId) {
        System.out.println("Payment confirmed for transaction ID: " + transactionId);
        // Logic to handle payment confirmation goes here
    }
}