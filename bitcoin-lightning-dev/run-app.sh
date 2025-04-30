#!/bin/bash

# Run script for Bitcoin and Lightning Network nodes
# This script starts all the necessary services and initializes the environment

# Colors for better readability
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

BASEDIR=$(dirname "$0")
cd "$BASEDIR"

# Function to check if a container is running
check_container_running() {
    local container_name="$1"
    if [ "$(docker ps -q -f name=$container_name)" ]; then
        return 0 # Container is running
    else
        return 1 # Container is not running
    fi
}

# Function to wait for a service to be ready
wait_for_service() {
    local service_name="$1"
    local check_command="$2"
    local max_attempts=30
    local attempt=1
    
    echo -e "${YELLOW}Waiting for $service_name to be ready...${NC}"
    
    while [ $attempt -le $max_attempts ]; do
        if eval "$check_command" > /dev/null 2>&1; then
            echo -e "${GREEN}$service_name is ready!${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo -e "\n${RED}Timeout waiting for $service_name to be ready!${NC}"
    return 1
}

echo -e "${BLUE}=======================================${NC}"
echo -e "${BLUE}   Bitcoin & Lightning Network App     ${NC}"
echo -e "${BLUE}=======================================${NC}"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Docker is not running. Please start Docker and try again.${NC}"
    exit 1
fi

# Check if containers are already running
if check_container_running "bitcoin-node" && check_container_running "lightning-node"; then
    echo -e "${GREEN}Bitcoin and Lightning nodes are already running.${NC}"
else
    echo -e "${YELLOW}Starting Bitcoin and Lightning nodes...${NC}"
    docker-compose up -d
    
    # Wait for Bitcoin node to be ready
    wait_for_service "Bitcoin node" "docker exec bitcoin-node bitcoin-cli -regtest -rpcuser=lnbhokycfu -rpcpassword=\"2.%cr.3,ck\\\\UDiUtqjad[afR]\" getblockchaininfo"
    
    # Initialize Bitcoin wallet
    echo -e "${YELLOW}Initializing Bitcoin wallet...${NC}"
    ./bitcoin-cli-helper.sh ensure_wallet
    
    # Check if we have funds in Bitcoin wallet
    BTC_BALANCE=$(./bitcoin-cli-helper.sh balance | grep -o '[0-9]\+\.[0-9]\+' || echo "0")
    if (( $(echo "$BTC_BALANCE < 1" | bc -l) )); then
        echo -e "${YELLOW}Mining initial blocks to generate coins...${NC}"
        ./bitcoin-cli-helper.sh mine 101
    fi
    
    # Wait for Lightning node to be ready
    wait_for_service "Lightning node" "docker exec lightning-node lncli --network=regtest getinfo"
    
    # Check if Lightning wallet needs to be unlocked
    WALLET_STATUS=$(docker exec lightning-node lncli --network=regtest getinfo 2>&1)
    if [[ $WALLET_STATUS == *"wallet is encrypted"* ]]; then
        echo -e "${YELLOW}Unlocking Lightning wallet...${NC}"
        ./bitcoin-cli-helper.sh ln-unlock
    fi
    
    # Check Lightning wallet balance
    LN_BALANCE=$(./bitcoin-cli-helper.sh ln-balance | grep -o '"confirmed_balance": "[0-9]\+"' | grep -o '[0-9]\+' || echo "0")
    if [ "$LN_BALANCE" -eq 0 ]; then
        echo -e "${YELLOW}Funding Lightning wallet...${NC}"
        ./bitcoin-cli-helper.sh setup-test
    fi
fi

# Display node information
echo -e "${BLUE}=======================================${NC}"
echo -e "${GREEN}Node Status:${NC}"
echo -e "${YELLOW}Bitcoin Node:${NC}"
./bitcoin-cli-helper.sh info | grep -E 'blocks|chain|connections|networkhashps'

echo -e "${YELLOW}Lightning Node:${NC}"
./bitcoin-cli-helper.sh ln-info | grep -E 'alias|identity_pubkey|block_height'

echo -e "${YELLOW}Wallet Balances:${NC}"
BTC_BALANCE=$(./bitcoin-cli-helper.sh balance)
LN_BALANCE=$(./bitcoin-cli-helper.sh ln-balance | grep -o '"confirmed_balance": "[0-9]\+"' | grep -o '[0-9]\+')
echo -e "Bitcoin: $BTC_BALANCE BTC"
echo -e "Lightning: $LN_BALANCE sats"

echo -e "${BLUE}=======================================${NC}"
echo -e "${GREEN}Available interfaces:${NC}"
echo -e "Bitcoin RPC: http://localhost:18443"
echo -e "Lightning RPC: http://localhost:10009"
echo -e "Lightning REST API: https://localhost:8080"
echo -e "${BLUE}=======================================${NC}"
echo -e "${GREEN}Using test helper scripts:${NC}"
echo -e "./bitcoin-cli-helper.sh - For Bitcoin commands"
echo -e "./rest-api-helper.sh - For Lightning REST API commands"
echo -e "./test-lightning-node.sh - For running test suite"
echo -e "${BLUE}=======================================${NC}"

echo -e "${GREEN}Application is up and running!${NC}"

# Make the script executable
chmod +x "$0"
