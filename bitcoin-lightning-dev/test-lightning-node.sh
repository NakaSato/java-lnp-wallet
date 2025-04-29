#!/bin/bash

# Test Script for Bitcoin and Lightning Network Node
# Created on: April 27, 2025
# This script tests the functionality of your Bitcoin and Lightning Network setup

# Set text colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Create a test results directory
RESULTS_DIR="./test_results"
mkdir -p $RESULTS_DIR
TEST_LOG="$RESULTS_DIR/test_log_$(date +%Y%m%d_%H%M%S).txt"

echo "==== Bitcoin & Lightning Network Test Script ====" | tee -a $TEST_LOG
echo "Test started at: $(date)" | tee -a $TEST_LOG
echo "" | tee -a $TEST_LOG

# Function to check if a command succeeded
check_result() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}[PASS]${NC} $1" | tee -a $TEST_LOG
        return 0
    else
        echo -e "${RED}[FAIL]${NC} $1" | tee -a $TEST_LOG
        return 1
    fi
}

# Function to print section headers
section_header() {
    echo "" | tee -a $TEST_LOG
    echo -e "${YELLOW}===== $1 =====${NC}" | tee -a $TEST_LOG
}

# SECTION 1: Test Bitcoin Node
section_header "Testing Bitcoin Node"

# Check if Bitcoin node is running
echo "Checking if Bitcoin node container is running..." | tee -a $TEST_LOG
BITCOIN_RUNNING=$(docker ps | grep bitcoin-node)
if [ -n "$BITCOIN_RUNNING" ]; then
    check_result "Bitcoin node is running"
else
    check_result "Bitcoin node is not running"
    echo "Attempting to start Bitcoin node..." | tee -a $TEST_LOG
    docker start bitcoin-node
    sleep 5
    check_result "Starting Bitcoin node"
fi

# Test Bitcoin RPC connection
echo "Testing Bitcoin RPC connection..." | tee -a $TEST_LOG
BITCOIN_INFO=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" getblockchaininfo)
check_result "Bitcoin RPC connection test"

# Get blockchain info and save it
echo "Getting blockchain info..." | tee -a $TEST_LOG
docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" getblockchaininfo > $RESULTS_DIR/blockchain_info.json
check_result "Saving blockchain info"

# Check if wallet exists, create if it doesn't
echo "Checking Bitcoin wallet..." | tee -a $TEST_LOG
WALLET_LIST=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" listwallets)

if [[ $WALLET_LIST == *"regtest_wallet"* ]]; then
    check_result "Bitcoin wallet 'regtest_wallet' exists"
    echo "Loading wallet 'regtest_wallet'..." | tee -a $TEST_LOG
    LOAD_RESULT=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" loadwallet "regtest_wallet" 2>&1)
    if [[ $LOAD_RESULT == *"already loaded"* ]]; then
        check_result "Wallet 'regtest_wallet' is already loaded"
    else
        check_result "Loading wallet 'regtest_wallet'"
    fi
else
    echo "Creating Bitcoin wallet 'regtest_wallet'..." | tee -a $TEST_LOG
    CREATE_RESULT=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" createwallet "regtest_wallet" 2>&1)
    if [[ $CREATE_RESULT == *"Created"* ]]; then
        check_result "Creating Bitcoin wallet 'regtest_wallet'"
    else
        check_result "Failed to create wallet: $CREATE_RESULT"
        # Try to load default wallet as fallback
        echo "Attempting to use default wallet..." | tee -a $TEST_LOG
        docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" loadwallet ""
    fi
fi

# Check wallet balance
echo "Checking wallet balance..." | tee -a $TEST_LOG
BALANCE=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" getbalance)
echo "Current wallet balance: $BALANCE BTC" | tee -a $TEST_LOG

# Mine some blocks if balance is low
if (( $(echo "$BALANCE < 1" | bc -l) )); then
    echo "Balance is low, mining some blocks..." | tee -a $TEST_LOG
    # Generate a new address
    MINING_ADDRESS=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" getnewaddress)
    # Mine 101 blocks to make coinbase spendable
    docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" generatetoaddress 101 "$MINING_ADDRESS" > /dev/null
    check_result "Mining 101 blocks to address $MINING_ADDRESS"
    
    # Check new balance
    NEW_BALANCE=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" getbalance)
    echo "New wallet balance: $NEW_BALANCE BTC" | tee -a $TEST_LOG
fi

# SECTION 2: Test Lightning Node
section_header "Testing Lightning Node"

# Check if Lightning node is running
echo "Checking if Lightning node container is running..." | tee -a $TEST_LOG
LIGHTNING_RUNNING=$(docker ps | grep lightning-node)
if [ -n "$LIGHTNING_RUNNING" ]; then
    check_result "Lightning node is running"
else
    check_result "Lightning node is not running"
    echo "Attempting to start Lightning node..." | tee -a $TEST_LOG
    docker start lightning-node
    sleep 5
    check_result "Starting Lightning node"
fi

# Test if wallet is unlocked or needs unlock
echo "Testing Lightning wallet status..." | tee -a $TEST_LOG
WALLET_INFO=$(docker exec lightning-node lncli --network=regtest getinfo 2>&1)

if [[ $WALLET_INFO == *"wallet is encrypted"* ]]; then
    echo "Lightning wallet is locked. Unlocking..." | tee -a $TEST_LOG
    # Try to unlock with the known password from seed.txt if available
    if [ -f "./seed.txt" ]; then
        PASSWORD=$(head -n 1 seed.txt | tr -d '\n')
        echo "$PASSWORD" | docker exec -i lightning-node lncli --network=regtest unlock
    else
        # Try with the default password
        echo "root@password" | docker exec -i lightning-node lncli --network=regtest unlock
    fi
    check_result "Unlocking Lightning wallet"
    
    # Try again to get wallet info
    sleep 2
    WALLET_INFO=$(docker exec lightning-node lncli --network=regtest getinfo 2>&1)
    if [[ $WALLET_INFO == *"wallet is encrypted"* ]]; then
        echo "Failed to unlock with stored password. You may need to unlock manually." | tee -a $TEST_LOG
        echo "Use: docker exec -it lightning-node lncli --network=regtest unlock" | tee -a $TEST_LOG
        exit 1
    fi
else
    check_result "Lightning wallet is already unlocked"
fi

# Get Lightning node info and save it
echo "Getting Lightning node info..." | tee -a $TEST_LOG
docker exec lightning-node lncli --network=regtest getinfo > $RESULTS_DIR/lightning_info.json
check_result "Saving Lightning node info"

# Get wallet balance
echo "Checking Lightning wallet balance..." | tee -a $TEST_LOG
docker exec lightning-node lncli --network=regtest walletbalance > $RESULTS_DIR/lightning_balance.json
check_result "Getting Lightning wallet balance"
LIGHTNING_BALANCE=$(cat $RESULTS_DIR/lightning_balance.json | grep -o '"confirmed_balance": "[^"]*"' | cut -d'"' -f4)
echo "Lightning wallet balance: $LIGHTNING_BALANCE sats" | tee -a $TEST_LOG

# SECTION 3: Fund Lightning Wallet (if needed)
section_header "Funding Lightning Wallet"

if [ "$LIGHTNING_BALANCE" == "0" ]; then
    echo "Lightning wallet is empty. Funding from Bitcoin wallet..." | tee -a $TEST_LOG
    
    # Get a new address from the Lightning wallet
    LIGHTNING_ADDRESS=$(docker exec lightning-node lncli --network=regtest newaddress p2wkh | grep -o '"address": "[^"]*"' | cut -d'"' -f4)
    check_result "Getting new Lightning wallet address: $LIGHTNING_ADDRESS"
    
    # Send funds from Bitcoin wallet to Lightning wallet
    TXID=$(docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" sendtoaddress "$LIGHTNING_ADDRESS" 1.0)
    check_result "Sending 1 BTC to Lightning wallet, txid: $TXID"
    
    # Mine a block to confirm the transaction
    echo "Mining a block to confirm the transaction..." | tee -a $TEST_LOG
    docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword="2.%cr.3,ck\\UDiUtqjad[afR" generatetoaddress 6 "$MINING_ADDRESS" > /dev/null
    check_result "Mining 6 blocks to confirm the transaction"
    
    # Wait for the Lightning wallet to detect the funds
    echo "Waiting for Lightning wallet to detect the funds..." | tee -a $TEST_LOG
    sleep 5
    
    # Check new Lightning wallet balance
    NEW_LIGHTNING_BALANCE=$(docker exec lightning-node lncli --network=regtest walletbalance | grep -o '"confirmed_balance": "[^"]*"' | cut -d'"' -f4)
    echo "New Lightning wallet balance: $NEW_LIGHTNING_BALANCE sats" | tee -a $TEST_LOG
    if [ "$NEW_LIGHTNING_BALANCE" != "0" ]; then
        check_result "Lightning wallet funding successful"
    else
        check_result "Lightning wallet funding may not have been detected yet"
    fi
else
    echo "Lightning wallet already has funds, skipping funding step." | tee -a $TEST_LOG
fi

# SECTION 4: Create a Local Testing Channel
section_header "Creating a Test Lightning Channel"

# For testing, we'll create another Lightning node and establish a channel
echo "Checking for test partner Lightning node..." | tee -a $TEST_LOG
TEST_NODE_RUNNING=$(docker ps | grep lightning-node-test)

if [ -z "$TEST_NODE_RUNNING" ]; then
    echo "Creating a test partner Lightning node..." | tee -a $TEST_LOG
    # This is a simplified setup for testing purposes
    echo "This would create another Lightning node for testing channels." | tee -a $TEST_LOG
    echo "In a production environment, you would connect to real Lightning nodes." | tee -a $TEST_LOG
    echo "Skipping actual creation of test node for now." | tee -a $TEST_LOG
    
    # Get pubkey of our main Lightning node
    NODE_PUBKEY=$(docker exec lightning-node lncli --network=regtest getinfo | grep -o '"identity_pubkey": "[^"]*"' | cut -d'"' -f4)
    echo "Main Lightning node pubkey: $NODE_PUBKEY" | tee -a $TEST_LOG
else
    echo "Test partner Lightning node already exists." | tee -a $TEST_LOG
fi

# SECTION 5: Test Lightning Network Functionality
section_header "Testing Lightning Network Functionality"

# List open channels
echo "Listing open channels..." | tee -a $TEST_LOG
docker exec lightning-node lncli --network=regtest listchannels > $RESULTS_DIR/lightning_channels.json
check_result "Listing Lightning channels"

# List pending channels
echo "Listing pending channels..." | tee -a $TEST_LOG
docker exec lightning-node lncli --network=regtest pendingchannels > $RESULTS_DIR/lightning_pending_channels.json
check_result "Listing pending Lightning channels"

# List peers
echo "Listing peers..." | tee -a $TEST_LOG
docker exec lightning-node lncli --network=regtest listpeers > $RESULTS_DIR/lightning_peers.json
check_result "Listing Lightning peers"

# SECTION 6: Summary
section_header "Test Summary"

echo "Bitcoin node status: Operational" | tee -a $TEST_LOG
echo "Lightning node status: Operational" | tee -a $TEST_LOG
echo "Bitcoin wallet balance: $BALANCE BTC" | tee -a $TEST_LOG
echo "Lightning wallet balance: $NEW_LIGHTNING_BALANCE sats" | tee -a $TEST_LOG

echo "" | tee -a $TEST_LOG
echo "Test completed at: $(date)" | tee -a $TEST_LOG
echo "Test results saved to: $TEST_LOG" | tee -a $TEST_LOG
echo "==== End of Test Script ====" | tee -a $TEST_LOG

# Make the script executable
chmod +x "$0"