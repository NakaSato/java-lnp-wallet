package com.lightning.network;

public class NodeConnection {
    private String nodeAddress;

    public NodeConnection(String nodeAddress) {
        this.nodeAddress = nodeAddress;
    }

    public void connect() {
        System.out.println("Connecting to Lightning node at: " + nodeAddress);
        // Simulate connection logic
    }

    public void disconnect() {
        System.out.println("Disconnecting from Lightning node at: " + nodeAddress);
        // Simulate disconnection logic
    }

    public void sendMessage(String message) {
        System.out.println("Sending message to Lightning node: " + message);
        // Simulate sending a message to the node
    }
}