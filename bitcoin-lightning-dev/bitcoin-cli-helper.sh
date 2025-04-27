#!/bin/bash

# Helper script for common bitcoin-cli commands
source .env

# Function to run bitcoin-cli command
bitcoin_cli() {
  docker exec bitcoin-node bitcoin-cli -rpcuser="$BITCOIN_RPC_USER" -rpcpassword="$BITCOIN_RPC_PASSWORD" "$@"
}

# Function to run lightning-cli command
lightning_cli() {
  docker exec lightning-node lncli --rpcuser="$LIGHTNING_RPC_USER" --rpcpassword="$LIGHTNING_RPC_PASSWORD" "$@"
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
    ADDRESS="$(bitcoin_cli getnewaddress)"
    bitcoin_cli generatetoaddress "$COUNT" "$ADDRESS"
    ;;
  "balance")
    bitcoin_cli getbalance
    ;;
  "ln-info")
    lightning_cli getinfo
    ;;
  "ln-balance")
    lightning_cli walletbalance
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
  *)
    echo "Usage: $0 {info|mine [blocks]|balance|ln-info|ln-balance|start|stop|logs}"
    exit 1
    ;;
esac
