package com.lightning.network;

/**
 * Class to hold the result of a connection attempt with diagnostic information
 */
public class ConnectionResult {
    private boolean success;
    private boolean fixed;
    private String diagnostics;
    private String errorMessage;
    
    public ConnectionResult() {
        this.success = false;
        this.fixed = false;
        this.diagnostics = "";
        this.errorMessage = "";
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public boolean isFixed() {
        return fixed;
    }
    
    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }
    
    public String getDiagnostics() {
        return diagnostics;
    }
    
    public void setDiagnostics(String diagnostics) {
        this.diagnostics = diagnostics != null ? diagnostics : "";
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }
}