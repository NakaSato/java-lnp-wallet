#!/bin/bash

echo "Setting up RTL with Lightning Node macaroons..."

# Make sure RTL directories exist
mkdir -p ./data/rtl/lnd/macaroons

# Copy macaroons from lightning-node to RTL
docker exec lightning-node cat /root/.lnd/data/chain/bitcoin/regtest/admin.macaroon > ./data/rtl/lnd/macaroons/admin.macaroon
docker exec lightning-node cat /root/.lnd/data/chain/bitcoin/regtest/readonly.macaroon > ./data/rtl/lnd/macaroons/readonly.macaroon
docker exec lightning-node cat /root/.lnd/data/chain/bitcoin/regtest/invoice.macaroon > ./data/rtl/lnd/macaroons/invoice.macaroon

# Copy TLS cert
docker exec -it lightning-node cat /root/.lnd/tls.cert > ./data/rtl/lnd/tls.cert

echo "Setup complete. You can now access RTL at http://localhost:3000"
echo "Default password is 'password' (from the multiPass setting in RTL-Config.json)"
