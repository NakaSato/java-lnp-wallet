package com.lightning.model;

/**
 * Model class for Lightning Network payments
 */
public class Payment {
    private String paymentHash;
    private String paymentPreimage;
    private String paymentRoute;
    private long valueSat;
    private long feeSat;
    
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
    
    /**
     * Get the total amount of the payment (value + fee)
     */
    public long getTotalSat() {
        return valueSat + feeSat;
    }
    
    /**
     * Format the amount as a string with proper units
     */
    public String getFormattedAmount() {
        return String.format("%d sats (%.8f BTC)", valueSat, valueSat / 100_000_000.0);
    }
    
    /**
     * Format the fee as a string with proper units
     */
    public String getFormattedFee() {
        return String.format("%d sats (%.8f BTC)", feeSat, feeSat / 100_000_000.0);
    }
}