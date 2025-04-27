package com.lightning.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Model class for Lightning Network invoices
 */
public class Invoice {
    private String paymentRequest;
    private String rHash;
    private String memo;
    private long amountSats;
    private boolean settled;
    private long creationDate;
    private long settleDate;
    private long addIndex;
    
    public String getPaymentRequest() {
        return paymentRequest;
    }
    
    public void setPaymentRequest(String paymentRequest) {
        this.paymentRequest = paymentRequest;
    }
    
    public String getRHash() {
        return rHash;
    }
    
    public void setRHash(String rHash) {
        this.rHash = rHash;
    }
    
    public String getMemo() {
        return memo;
    }
    
    public void setMemo(String memo) {
        this.memo = memo;
    }
    
    public long getAmountSats() {
        return amountSats;
    }
    
    public void setAmountSats(long amountSats) {
        this.amountSats = amountSats;
    }
    
    public boolean isSettled() {
        return settled;
    }
    
    public void setSettled(boolean settled) {
        this.settled = settled;
    }
    
    public long getCreationDate() {
        return creationDate;
    }
    
    public void setCreationDate(long creationDate) {
        this.creationDate = creationDate;
    }
    
    public long getSettleDate() {
        return settleDate;
    }
    
    public void setSettleDate(long settleDate) {
        this.settleDate = settleDate;
    }
    
    public long getAddIndex() {
        return addIndex;
    }
    
    public void setAddIndex(long addIndex) {
        this.addIndex = addIndex;
    }
    
    /**
     * Format creation date as human-readable string
     */
    public String getFormattedCreationDate() {
        if (creationDate == 0) {
            return "N/A";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(creationDate), ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
    
    /**
     * Format settle date as human-readable string
     */
    public String getFormattedSettleDate() {
        if (settleDate == 0) {
            return "N/A";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(settleDate), ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
    
    /**
     * Format the amount as a Bitcoin value with proper decimal places
     */
    public String getFormattedAmount() {
        return String.format("%d sats (%.8f BTC)", amountSats, amountSats / 100_000_000.0);
    }
}