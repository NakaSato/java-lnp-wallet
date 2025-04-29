#!/bin/bash

# Helper script for common bitcoin-cli commands
# Hard-coding credentials for now to fix connection issues
BITCOIN_RPC_USER="lnbhokycfu"
BITCOIN_RPC_PASSWORD="2.%cr.3,ck\\UDiUtqjad[afR"

# Function to run bitcoin-cli command
bitcoin_cli() {
  docker exec bitcoin-node bitcoin-cli -regtest -rpcuser="$BITCOIN_RPC_USER" -rpcpassword="$BITCOIN_RPC_PASSWORD" "$@"
}

# Function to run lightning-cli command
lightning_cli() {
  docker exec lightning-node lncli --network=regtest "$@"
}

# Function to ensure a wallet is created and loaded
ensure_wallet() {
  local wallet_name="${1:-regtest_wallet}"
  
  # Check if wallet exists
  local wallets=$(bitcoin_cli listwallets)
  
  if [[ $wallets == *"$wallet_name"* ]]; then
    echo "Wallet '$wallet_name' is already loaded."
  else
    # First try to load the wallet (it might exist but not be loaded)
    local load_result=$(bitcoin_cli loadwallet "$wallet_name" 2>&1)
    
    if [[ $load_result == *"already loaded"* ]] || [[ $load_result == *"success"* ]]; then
      echo "Wallet '$wallet_name' loaded successfully."
    else
      # Create the wallet if it doesn't exist
      local create_result=$(bitcoin_cli createwallet "$wallet_name" 2>&1)
      
      if [[ $create_result == *"Created"* ]]; then
        echo "Wallet '$wallet_name' created successfully."
      else
        echo "Error creating wallet: $create_result"
        return 1
      fi
    fi
  fi
  
  return 0
}

case "$1" in
  "info")
    bitcoin_cli getblockchaininfo
    ;;
  "mine")
    if [ -z "$2" ]; then
      COUNT="1"
    else
      COUNT="$2"
    fi
    ensure_wallet
    ADDRESS="$(bitcoin_cli getnewaddress)"
    bitcoin_cli generatetoaddress "$COUNT" "$ADDRESS"
    ;;
  "balance")
    ensure_wallet
    bitcoin_cli getbalance
    ;;
  "create-wallet")
    NAME="${2:-regtest_wallet}"
    bitcoin_cli createwallet "$NAME"
    ;;
  "load-wallet")
    NAME="${2:-regtest_wallet}"
    bitcoin_cli loadwallet "$NAME"
    ;;
  "ln-info")
    lightning_cli getinfo
    ;;
  "ln-balance")
    lightning_cli walletbalance
    ;;
  "ln-unlock")
    if [ -f "./seed.txt" ]; then
      PASSWORD=$(head -n 1 seed.txt | tr -d '\n')
      echo "$PASSWORD" | docker exec -i lightning-node lncli --network="$LIGHTNING_NETWORK" unlock
    else
      # Default password prompt
      lightning_cli unlock
    fi
    ;;
  "ln-newaddress")
    lightning_cli newaddress p2wkh
    ;;
  "start")
    docker-compose up -d
    ;;
  "stop")
    docker-compose down
    ;;
  "logs")
    docker-compose logs -f
    ;;
  "setup-test")
    # Setup for testing - creates wallet, mines initial blocks, funds lightning
    ensure_wallet
    echo "Mining initial blocks..."
    ADDRESS="$(bitcoin_cli getnewaddress)"
    bitcoin_cli generatetoaddress 101 "$ADDRESS"
    
    echo "Checking Lightning wallet..."
    LN_BALANCE=$(lightning_cli walletbalance | grep -o '"confirmed_balance": "[^"]*"' | cut -d'"' -f4)
    
    if [ "$LN_BALANCE" == "0" ]; then
      echo "Funding Lightning wallet..."
      LN_ADDRESS=$(lightning_cli newaddress p2wkh | grep -o '"address": "[^"]*"' | cut -d'"' -f4)
      TXID=$(bitcoin_cli sendtoaddress "$LN_ADDRESS" 1.0)
      echo "Sent 1 BTC to Lightning wallet, txid: $TXID"
      
      echo "Mining 6 confirmation blocks..."
      bitcoin_cli generatetoaddress 6 "$ADDRESS"
      echo "Lightning wallet should now be funded. Check with ./bitcoin-cli-helper.sh ln-balance"
    else
      echo "Lightning wallet already has funds: $LN_BALANCE sats"
    fi
    ;;
  *)
    echo "Usage: $0 {info|mine [blocks]|balance|create-wallet [name]|load-wallet [name]|ln-info|ln-balance|ln-unlock|ln-newaddress|start|stop|logs|setup-test}"
    exit 1
    ;;
esac
