#!/bin/bash

# Simple Bitcoin to Lightning Transfer Script
echo "🚀 Simple Bitcoin to Lightning Transfer"
echo "======================================="

# Step 1: Check if wallet exists and load it
echo "📱 Step 1: Loading wallet..."
docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass loadwallet "main" 2>/dev/null || echo "Wallet already loaded"

# Step 2: Get Lightning address
echo "⚡ Step 2: Getting Lightning wallet address..."
LN_ADDRESS=$(docker exec lightning-node lncli --network=regtest newaddress p2wkh | grep '"address"' | awk -F'"' '{print $4}')
echo "Lightning address: $LN_ADDRESS"

# Step 3: Send with explicit fee rate
echo "💸 Step 3: Sending 0.01 BTC to Lightning wallet..."
AMOUNT="0.01"
FEE_RATE="1"  # 1 sat/vB

TX_ID=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass sendtoaddress "$LN_ADDRESS" "$AMOUNT" "" "" false false $FEE_RATE)

if [ $? -eq 0 ]; then
    echo "✅ Transaction sent successfully!"
    echo "Transaction ID: $TX_ID"
    
    # Step 4: Mine a block to confirm
    echo "⛏️  Step 4: Mining block to confirm transaction..."
    NEW_ADDRESS=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass getnewaddress)
    docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass generatetoaddress 1 $NEW_ADDRESS > /dev/null
    
    sleep 3
    
    # Step 5: Check Lightning balance
    echo "💫 Step 5: Checking Lightning wallet balance..."
    LN_BALANCE=$(docker exec lightning-node lncli --network=regtest walletbalance)
    TOTAL_SATS=$(echo "$LN_BALANCE" | grep '"total_balance"' | awk -F'"' '{print $4}')
    TOTAL_BTC=$(echo "scale=8; $TOTAL_SATS / 100000000" | bc -l)
    echo "Lightning wallet: $TOTAL_SATS sats ($TOTAL_BTC BTC)"
    
    # Step 6: Create Lightning invoice
    echo "🧾 Step 6: Creating Lightning invoice for 10,000 sats..."
    INVOICE_RESPONSE=$(docker exec lightning-node lncli --network=regtest addinvoice --amt=10000 --memo="Demo payment")
    PAYMENT_REQUEST=$(echo "$INVOICE_RESPONSE" | grep '"payment_request"' | awk -F'"' '{print $4}')
    
    echo "✅ Invoice created!"
    echo "Payment Request: $PAYMENT_REQUEST"
    
    echo ""
    echo "🎉 Success! You have successfully:"
    echo "  1. ✅ Minted Bitcoin (via mining)"
    echo "  2. ✅ Funded Lightning wallet with $AMOUNT BTC"
    echo "  3. ✅ Created Lightning invoice for 10,000 sats"
    echo ""
    echo "💡 Next steps:"
    echo "  - Pay invoice: docker exec lightning-node lncli --network=regtest payinvoice $PAYMENT_REQUEST"
    echo "  - Check RTL: http://localhost:3000"
    
else
    echo "❌ Transaction failed"
fi
