#!/bin/bash

# REST API helper script for Lightning Node
# Usage: ./rest-api-helper.sh <endpoint>
# Example: ./rest-api-helper.sh getinfo

ENDPOINT=$1

if [ -z "$ENDPOINT" ]; then
    echo "Usage: $0 <endpoint>"
    echo "Example: $0 getinfo"
    exit 1
fi

# Get the hex-encoded macaroon
HEX_MACAROON=$(xxd -ps -u -c 1000 data/lightning/data/chain/bitcoin/regtest/admin.macaroon)

echo "Using macaroon: ${HEX_MACAROON:0:30}... (truncated)"

# Use curl to directly access the REST API with verbose output
curl -v -k \
    -X GET \
    -H "Content-Type: application/json" \
    -H "Grpc-Metadata-macaroon: $HEX_MACAROON" \
    https://localhost:8080/v1/$ENDPOINT
echo ""

