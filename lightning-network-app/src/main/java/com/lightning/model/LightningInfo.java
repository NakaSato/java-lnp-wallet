package com.lightning.model;

/**
 * Model class for storing information about the Lightning Node
 */
public class LightningInfo {
    private String identityPubkey;
    private String alias;
    private int numActiveChannels;
    private int numPendingChannels;
    private int numPeers;
    private int blockHeight;
    private boolean syncedToChain;

    public String getIdentityPubkey() {
        return identityPubkey;
    }

    public void setIdentityPubkey(String identityPubkey) {
        this.identityPubkey = identityPubkey;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public int getNumActiveChannels() {
        return numActiveChannels;
    }

    public void setNumActiveChannels(int numActiveChannels) {
        this.numActiveChannels = numActiveChannels;
    }

    public int getNumPendingChannels() {
        return numPendingChannels;
    }

    public void setNumPendingChannels(int numPendingChannels) {
        this.numPendingChannels = numPendingChannels;
    }

    public int getNumPeers() {
        return numPeers;
    }

    public void setNumPeers(int numPeers) {
        this.numPeers = numPeers;
    }

    public int getBlockHeight() {
        return blockHeight;
    }

    public void setBlockHeight(int blockHeight) {
        this.blockHeight = blockHeight;
    }

    public boolean isSyncedToChain() {
        return syncedToChain;
    }

    public void setSyncedToChain(boolean syncedToChain) {
        this.syncedToChain = syncedToChain;
    }
}