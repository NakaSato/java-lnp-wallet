#!/bin/bash
set -e

# Start LND
./lnd "$@" &
LND_PID=$!

# Wait for LND to start
echo "Waiting for LND to start..."
sleep 5

# Check if wallet exists and create if needed
if ! ls /root/.lnd/data/chain/bitcoin/regtest/wallet.db 2>/dev/null; then
  echo "Creating new wallet..."
  # Create wallet with no password
  echo "Creating wallet with no password for development"
  ./lncli --network=regtest create --insecure_wallet_seed
  
  # Fund wallet with initial coins in regtest mode
  echo "Generating initial blocks for regtest mode"
  ADDR=$(./lncli --network=regtest newaddress p2wkh | jq -r '.address')
  curl -s -u bitcoin:bitcoin -X POST -H 'Content-Type: application/json' http://bitcoind:18443/ \
    -d "{\"jsonrpc\":\"1.0\",\"method\":\"generatetoaddress\",\"params\":[101,\"$ADDR\"],\"id\":1}"
  sleep 2
  ./lncli --network=regtest walletbalance
fi

# Wait for LND process to finish
wait $LND_PID