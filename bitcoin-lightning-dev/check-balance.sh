#!/bin/bash

echo "=== Bitcoin and Lightning Network Balance Summary ==="
echo "Date: $(date)"
echo ""

# Check if containers are running
echo "🔍 Checking container status..."
BITCOIN_STATUS=$(docker ps --filter "name=bitcoin-node" --format "table {{.Status}}" | grep -v STATUS)
LIGHTNING_STATUS=$(docker ps --filter "name=lightning-node" --format "table {{.Status}}" | grep -v STATUS)

if [ -z "$BITCOIN_STATUS" ]; then
    echo "❌ Bitcoin node is not running"
    exit 1
else
    echo "✅ Bitcoin node: $BITCOIN_STATUS"
fi

if [ -z "$LIGHTNING_STATUS" ]; then
    echo "❌ Lightning node is not running"
    exit 1
else
    echo "✅ Lightning node: $LIGHTNING_STATUS"
fi

echo ""

# Bitcoin Core Balance
echo "💰 BITCOIN CORE WALLET BALANCE"
echo "================================="
BITCOIN_BALANCE=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass getbalance 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "Total Balance: $BITCOIN_BALANCE BTC"
    
    # Get unconfirmed balance
    UNCONFIRMED=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass getunconfirmedbalance 2>/dev/null)
    echo "Unconfirmed Balance: $UNCONFIRMED BTC"
    
    # Get wallet info
    WALLET_INFO=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass getwalletinfo 2>/dev/null)
    if [ $? -eq 0 ]; then
        IMMATURE=$(echo "$WALLET_INFO" | grep '"immature_balance"' | awk -F': ' '{print $2}' | tr -d ',')
        echo "Immature Balance: $IMMATURE BTC"
    fi
else
    echo "❌ Could not retrieve Bitcoin balance (wallet may not exist)"
fi

echo ""

# Lightning Wallet Balance
echo "⚡ LIGHTNING WALLET BALANCE"
echo "==========================="
LN_WALLET_BALANCE=$(docker exec lightning-node lncli --network=regtest walletbalance 2>/dev/null)
if [ $? -eq 0 ]; then
    TOTAL_SAT=$(echo "$LN_WALLET_BALANCE" | grep '"total_balance"' | awk -F'"' '{print $4}')
    CONFIRMED_SAT=$(echo "$LN_WALLET_BALANCE" | grep '"confirmed_balance"' | awk -F'"' '{print $4}')
    UNCONFIRMED_SAT=$(echo "$LN_WALLET_BALANCE" | grep '"unconfirmed_balance"' | awk -F'"' '{print $4}')
    
    # Convert satoshis to BTC
    TOTAL_BTC=$(echo "scale=8; $TOTAL_SAT / 100000000" | bc -l 2>/dev/null || echo "0.00000000")
    CONFIRMED_BTC=$(echo "scale=8; $CONFIRMED_SAT / 100000000" | bc -l 2>/dev/null || echo "0.00000000")
    UNCONFIRMED_BTC=$(echo "scale=8; $UNCONFIRMED_SAT / 100000000" | bc -l 2>/dev/null || echo "0.00000000")
    
    echo "Total Balance: $TOTAL_SAT sats ($TOTAL_BTC BTC)"
    echo "Confirmed Balance: $CONFIRMED_SAT sats ($CONFIRMED_BTC BTC)"
    echo "Unconfirmed Balance: $UNCONFIRMED_SAT sats ($UNCONFIRMED_BTC BTC)"
else
    echo "❌ Could not retrieve Lightning wallet balance"
fi

echo ""

# Lightning Channel Balance
echo "🔗 LIGHTNING CHANNEL BALANCE"
echo "============================"
LN_CHANNEL_BALANCE=$(docker exec lightning-node lncli --network=regtest channelbalance 2>/dev/null)
if [ $? -eq 0 ]; then
    LOCAL_SAT=$(echo "$LN_CHANNEL_BALANCE" | grep -A1 '"local_balance"' | grep '"sat"' | awk -F'"' '{print $4}')
    REMOTE_SAT=$(echo "$LN_CHANNEL_BALANCE" | grep -A1 '"remote_balance"' | grep '"sat"' | awk -F'"' '{print $4}')
    
    # Convert to BTC
    LOCAL_BTC=$(echo "scale=8; $LOCAL_SAT / 100000000" | bc -l 2>/dev/null || echo "0.00000000")
    REMOTE_BTC=$(echo "scale=8; $REMOTE_SAT / 100000000" | bc -l 2>/dev/null || echo "0.00000000")
    
    echo "Local Balance (your funds): $LOCAL_SAT sats ($LOCAL_BTC BTC)"
    echo "Remote Balance (partner funds): $REMOTE_SAT sats ($REMOTE_BTC BTC)"
    
    # Total channel capacity
    TOTAL_CHANNEL_SAT=$((LOCAL_SAT + REMOTE_SAT))
    TOTAL_CHANNEL_BTC=$(echo "scale=8; $TOTAL_CHANNEL_SAT / 100000000" | bc -l 2>/dev/null || echo "0.00000000")
    echo "Total Channel Capacity: $TOTAL_CHANNEL_SAT sats ($TOTAL_CHANNEL_BTC BTC)"
else
    echo "❌ Could not retrieve Lightning channel balance"
fi

echo ""

# Summary
echo "📊 TOTAL SUMMARY"
echo "================"
TOTAL_BITCOIN=${BITCOIN_BALANCE:-0}
TOTAL_LN_WALLET_BTC=${TOTAL_BTC:-0.00000000}
TOTAL_LN_LOCAL_BTC=${LOCAL_BTC:-0.00000000}

# Calculate grand total (requires bc for floating point)
if command -v bc >/dev/null 2>&1; then
    GRAND_TOTAL=$(echo "scale=8; $TOTAL_BITCOIN + $TOTAL_LN_WALLET_BTC + $TOTAL_LN_LOCAL_BTC" | bc -l)
    echo "Bitcoin Core Wallet: $TOTAL_BITCOIN BTC"
    echo "Lightning Wallet: $TOTAL_LN_WALLET_BTC BTC"
    echo "Lightning Channels (your funds): $TOTAL_LN_LOCAL_BTC BTC"
    echo "─────────────────────────────────"
    echo "GRAND TOTAL: $GRAND_TOTAL BTC"
else
    echo "Bitcoin Core Wallet: $TOTAL_BITCOIN BTC"
    echo "Lightning Wallet: $TOTAL_LN_WALLET_BTC BTC" 
    echo "Lightning Channels (your funds): $TOTAL_LN_LOCAL_BTC BTC"
    echo "(Install 'bc' for automatic total calculation)"
fi

echo ""
echo "✅ Balance check completed!"
