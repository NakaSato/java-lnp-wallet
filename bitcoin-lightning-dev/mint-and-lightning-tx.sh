#!/bin/bash

# Bitcoin Mint and Lightning Network Transaction Script
# This script mints Bitcoin and sends it via Lightning Network

set -e  # Exit on any error

echo "🚀 Bitcoin Mint and Lightning Network Transaction"
echo "================================================="
echo "Date: $(date)"
echo ""

# Configuration
MINT_AMOUNT=${1:-0.1}  # Default 0.1 BTC if no argument provided
LN_FUNDING_AMOUNT=${2:-0.05}  # Default 0.05 BTC for Lightning funding
PAYMENT_AMOUNT_SATS=${3:-10000}  # Default 10,000 sats payment

echo "📋 Configuration:"
echo "  - Mint Amount: $MINT_AMOUNT BTC"
echo "  - Lightning Funding: $LN_FUNDING_AMOUNT BTC"
echo "  - Payment Amount: $PAYMENT_AMOUNT_SATS sats"
echo ""

# Step 1: Check if containers are running
echo "🔍 Step 1: Checking container status..."
if ! docker ps | grep -q "bitcoin-node"; then
    echo "❌ Bitcoin node is not running. Please start with: docker-compose up -d bitcoin"
    exit 1
fi

if ! docker ps | grep -q "lightning-node"; then
    echo "❌ Lightning node is not running. Please start with: docker-compose up -d lightning"
    exit 1
fi
echo "✅ All containers are running"
echo ""

# Step 2: Mint new Bitcoin (mine blocks)
echo "⛏️  Step 2: Minting new Bitcoin..."
NEW_ADDRESS=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass getnewaddress)
echo "  Generated new address: $NEW_ADDRESS"

# Calculate blocks needed (each block gives 50 BTC in regtest)
BLOCKS_NEEDED=$(echo "$MINT_AMOUNT / 50" | bc -l | awk '{print int($1+1)}')
if [ $BLOCKS_NEEDED -lt 1 ]; then
    BLOCKS_NEEDED=1
fi

echo "  Mining $BLOCKS_NEEDED block(s) to generate ~$MINT_AMOUNT BTC..."
MINED_BLOCKS=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass generatetoaddress $BLOCKS_NEEDED $NEW_ADDRESS)
echo "  ✅ Mined $BLOCKS_NEEDED block(s)"

# Wait a moment for the blocks to be processed
sleep 2
echo ""

# Step 3: Check updated Bitcoin balance
echo "💰 Step 3: Checking updated Bitcoin balance..."
BITCOIN_BALANCE=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass getbalance)
echo "  Current Bitcoin balance: $BITCOIN_BALANCE BTC"
echo ""

# Step 4: Fund Lightning wallet
echo "⚡ Step 4: Funding Lightning wallet..."
LN_ADDRESS=$(docker exec lightning-node lncli --network=regtest newaddress p2wkh | grep '"address"' | awk -F'"' '{print $4}')
echo "  Lightning wallet address: $LN_ADDRESS"

echo "  Sending $LN_FUNDING_AMOUNT BTC to Lightning wallet..."
TX_ID=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass sendtoaddress $LN_ADDRESS $LN_FUNDING_AMOUNT)
echo "  Transaction ID: $TX_ID"

# Mine a block to confirm the transaction
echo "  Mining 1 block to confirm transaction..."
docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass generatetoaddress 1 $NEW_ADDRESS > /dev/null
sleep 2
echo "  ✅ Transaction confirmed"
echo ""

# Step 5: Check Lightning wallet balance
echo "💫 Step 5: Checking Lightning wallet balance..."
LN_BALANCE=$(docker exec lightning-node lncli --network=regtest walletbalance)
TOTAL_SATS=$(echo "$LN_BALANCE" | grep '"total_balance"' | awk -F'"' '{print $4}')
TOTAL_BTC=$(echo "scale=8; $TOTAL_SATS / 100000000" | bc -l)
echo "  Lightning wallet balance: $TOTAL_SATS sats ($TOTAL_BTC BTC)"
echo ""

# Step 6: Create a Lightning invoice for demonstration
echo "🧾 Step 6: Creating Lightning invoice..."
INVOICE_RESPONSE=$(docker exec lightning-node lncli --network=regtest addinvoice --amt=$PAYMENT_AMOUNT_SATS --memo="Test payment from mint to Lightning")
PAYMENT_REQUEST=$(echo "$INVOICE_RESPONSE" | grep '"payment_request"' | awk -F'"' '{print $4}')
R_HASH=$(echo "$INVOICE_RESPONSE" | grep '"r_hash"' | awk -F'"' '{print $4}')

echo "  ✅ Invoice created successfully!"
echo "  Payment Request: $PAYMENT_REQUEST"
echo "  R-Hash: $R_HASH"
echo ""

# Step 7: Decode the invoice to show details
echo "🔍 Step 7: Invoice details..."
DECODED_INVOICE=$(docker exec lightning-node lncli --network=regtest decodepayreq $PAYMENT_REQUEST)
echo "$DECODED_INVOICE"
echo ""

# Step 8: Show summary
echo "📊 Step 8: Transaction Summary"
echo "=============================="
FINAL_BITCOIN_BALANCE=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=bitcoinpass getbalance)
FINAL_LN_BALANCE=$(docker exec lightning-node lncli --network=regtest walletbalance | grep '"total_balance"' | awk -F'"' '{print $4}')
FINAL_LN_BTC=$(echo "scale=8; $FINAL_LN_BALANCE / 100000000" | bc -l)

echo "Final Balances:"
echo "  💰 Bitcoin Core: $FINAL_BITCOIN_BALANCE BTC"
echo "  ⚡ Lightning Wallet: $FINAL_LN_BALANCE sats ($FINAL_LN_BTC BTC)"
echo ""

echo "✅ Mint and Lightning funding completed successfully!"
echo ""
echo "🔗 Next steps you can take:"
echo "  1. Pay the invoice: lncli --network=regtest payinvoice $PAYMENT_REQUEST"
echo "  2. Check invoice status: lncli --network=regtest lookupinvoice $R_HASH"
echo "  3. Open channels with other nodes for routing"
echo "  4. View in RTL interface: http://localhost:3000"
echo ""

# Save transaction details to a file
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="transaction_log_$TIMESTAMP.json"

cat > "$LOG_FILE" << EOF
{
  "timestamp": "$(date -Iseconds)",
  "operation": "mint_and_lightning_funding",
  "mint_amount_btc": "$MINT_AMOUNT",
  "funding_amount_btc": "$LN_FUNDING_AMOUNT",
  "payment_amount_sats": "$PAYMENT_AMOUNT_SATS",
  "bitcoin_address": "$NEW_ADDRESS",
  "lightning_address": "$LN_ADDRESS",
  "funding_tx_id": "$TX_ID",
  "invoice": {
    "payment_request": "$PAYMENT_REQUEST",
    "r_hash": "$R_HASH",
    "amount_sats": "$PAYMENT_AMOUNT_SATS"
  },
  "final_balances": {
    "bitcoin_core_btc": "$FINAL_BITCOIN_BALANCE",
    "lightning_wallet_sats": "$FINAL_LN_BALANCE",
    "lightning_wallet_btc": "$FINAL_LN_BTC"
  }
}
EOF

echo "📝 Transaction details saved to: $LOG_FILE"
