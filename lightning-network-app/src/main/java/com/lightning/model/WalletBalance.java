package com.lightning.model;

/**
 * Model class for storing wallet balance information
 */
public class WalletBalance {
    private long totalBalance;
    private long confirmedBalance;
    private long unconfirmedBalance;

    public long getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(long totalBalance) {
        this.totalBalance = totalBalance;
    }

    public long getConfirmedBalance() {
        return confirmedBalance;
    }

    public void setConfirmedBalance(long confirmedBalance) {
        this.confirmedBalance = confirmedBalance;
    }

    public long getUnconfirmedBalance() {
        return unconfirmedBalance;
    }

    public void setUnconfirmedBalance(long unconfirmedBalance) {
        this.unconfirmedBalance = unconfirmedBalance;
    }
    
    /**
     * Format the balance as a Bitcoin value with proper decimal places
     */
    public String formatTotalBalanceBtc() {
        return formatSatsToBtc(totalBalance);
    }
    
    /**
     * Format a satoshi amount to a Bitcoin string
     */
    public static String formatSatsToBtc(long sats) {
        return String.format("%.8f", sats / 100_000_000.0);
    }
}