package com.lightning.model;

/**
 * Model class for Lightning Network payment data
 */
public class Payment {
    private String paymentHash;
    private String paymentPreimage;
    private String paymentRoute;
    private long valueSat;
    private long feeSat;
    private String destination;
    private String description;
    private long timestamp;
    private long numSatoshis;
    
    public String getPaymentHash() {
        return paymentHash;
    }
    
    public void setPaymentHash(String paymentHash) {
        this.paymentHash = paymentHash;
    }
    
    public String getPaymentPreimage() {
        return paymentPreimage;
    }
    
    public void setPaymentPreimage(String paymentPreimage) {
        this.paymentPreimage = paymentPreimage;
    }
    
    public String getPaymentRoute() {
        return paymentRoute;
    }
    
    public void setPaymentRoute(String paymentRoute) {
        this.paymentRoute = paymentRoute;
    }
    
    public long getValueSat() {
        return valueSat;
    }
    
    public void setValueSat(long valueSat) {
        this.valueSat = valueSat;
    }
    
    public long getFeeSat() {
        return feeSat;
    }
    
    public void setFeeSat(long feeSat) {
        this.feeSat = feeSat;
    }
    
    public String getDestination() {
        return destination;
    }
    
    public void setDestination(String destination) {
        this.destination = destination;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getNumSatoshis() {
        return numSatoshis;
    }
    
    public void setNumSatoshis(long numSatoshis) {
        this.numSatoshis = numSatoshis;
    }
    
    /**
     * Format the payment amount as a readable string
     */
    public String getFormattedAmount() {
        return String.format("%d sats (%.8f BTC)", valueSat, valueSat / 100_000_000.0);
    }
    
    /**
     * Format the fee amount as a readable string
     */
    public String getFormattedFee() {
        return String.format("%d sats (%.8f BTC)", feeSat, feeSat / 100_000_000.0);
    }
}