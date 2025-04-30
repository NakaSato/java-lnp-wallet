package com.lightning.model;

/**
 * Represents the result of a connection attempt
 */
public class ConnectionResult {
	
	private final boolean success;
	private final String message;
	private final Exception error;

	private ConnectionResult(boolean success, String message, Exception error) {
		this.success = success;
		this.message = message;
		this.error = error;
	}

	public static ConnectionResult success(String message) {
		return new ConnectionResult(true, message, null);
	}

	public static ConnectionResult failure(String message, Exception error) {
		return new ConnectionResult(false, message, error);
	}

	public boolean isSuccess() {
		return success;
	}

	public String getMessage() {
		return message;
	}

	public Exception getError() {
		return error;
	}
}
