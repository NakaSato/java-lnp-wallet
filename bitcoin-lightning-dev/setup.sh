#!/bin/bash

# Setup script for Bitcoin and Lightning nodes
# This script will create a .env file with secure random values

# Colors for better readability
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to generate secure random password with special characters
generate_random_password() {
  local length=${1:-16}
  LC_ALL=C tr -dc 'a-zA-Z0-9!@#$%^&*()-_=+' < /dev/urandom | head -c "$length"
}

# Function to generate random username
generate_random_username() {
  local length=${1:-8}
  LC_ALL=C tr -dc 'a-z' < /dev/urandom | head -c "$length"
}

# Function to display help
show_help() {
  echo -e "${BLUE}Bitcoin & Lightning Node Setup Script${NC}"
  echo "Usage: $0 [OPTIONS]"
  echo ""
  echo "Options:"
  echo "  -h, --help                 Show this help message"
  echo "  -n, --network NETWORK      Set Bitcoin network (regtest, testnet, mainnet)"
  echo "  -e, --environment ENV      Set environment (development, production)"
  echo "  -f, --force                Force regeneration of .env file"
  echo ""
}

# Process command line arguments
FORCE_REGENERATE="false"
SPECIFIED_NETWORK=""
SPECIFIED_ENV=""

while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      show_help
      exit 0
      ;;
    -n|--network)
      SPECIFIED_NETWORK="$2"
      if [[ ! "$SPECIFIED_NETWORK" =~ ^(regtest|testnet|mainnet)$ ]]; then
        echo -e "${RED}Error: Network must be regtest, testnet, or mainnet${NC}"
        exit 1
      fi
      shift 2
      ;;
    -e|--environment)
      SPECIFIED_ENV="$2"
      if [[ ! "$SPECIFIED_ENV" =~ ^(development|production)$ ]]; then
        echo -e "${RED}Error: Environment must be development or production${NC}"
        exit 1
      fi
      shift 2
      ;;
    -f|--force)
      FORCE_REGENERATE="true"
      shift
      ;;
    *)
      echo -e "${RED}Unknown option: $1${NC}"
      show_help
      exit 1
      ;;
  esac
done

# Check if .env file exists and not forcing regeneration
if [[ -f ".env" && "$FORCE_REGENERATE" = "false" ]]; then
  echo -e "${YELLOW}Loading configuration from existing .env file${NC}"
  source .env
  USING_EXISTING="true"
else
  if [[ "$FORCE_REGENERATE" = "true" && -f ".env" ]]; then
    echo -e "${YELLOW}Forcing regeneration of .env file${NC}"
    mv .env .env.backup.$(date +%Y%m%d%H%M%S)
    echo -e "${GREEN}Backed up existing .env file${NC}"
  else
    echo -e "${YELLOW}No .env file found. Creating new one with random values${NC}"
  fi
  USING_EXISTING="false"
  
  # Generate or use specified values
  if [[ -n "$SPECIFIED_NETWORK" ]]; then
    BITCOIN_NETWORK="$SPECIFIED_NETWORK"
  else
    # Weighted random selection favoring regtest for development
    if [[ $((RANDOM % 100)) -lt 70 ]]; then
      BITCOIN_NETWORK="regtest"
    elif [[ $((RANDOM % 100)) -lt 95 ]]; then
      BITCOIN_NETWORK="testnet"
    else
      BITCOIN_NETWORK="mainnet"
    fi
  fi
  
  LIGHTNING_NETWORK="$BITCOIN_NETWORK"
  BITCOIN_RPC_USER="$(generate_random_username 10)"
  BITCOIN_RPC_PASSWORD="$(generate_random_password 24)"
  LIGHTNING_RPC_USER="$(generate_random_username 10)"
  LIGHTNING_RPC_PASSWORD="$(generate_random_password 24)"
  
  if [[ -n "$SPECIFIED_ENV" ]]; then
    NODE_ENV="$SPECIFIED_ENV"
  else
    # Default to development unless it's mainnet
    if [[ "$BITCOIN_NETWORK" == "mainnet" ]]; then
      NODE_ENV="production"
    else
      NODE_ENV="development"
    fi
  fi

  # Generate additional settings for better security
  WALLET_ENCRYPTION_KEY="$(generate_random_password 32)"
  RPC_PORT="$(( BITCOIN_NETWORK == "mainnet" ? 8332 : (BITCOIN_NETWORK == "testnet" ? 18332 : 18443) ))"
fi

# Show settings in a nice format
echo -e "${BLUE}Bitcoin and Lightning Environment Setup${NC}"
echo -e "${BLUE}===============================================${NC}"
echo -e "${GREEN}Bitcoin Network:${NC} $BITCOIN_NETWORK"
echo -e "${GREEN}Lightning Network:${NC} $LIGHTNING_NETWORK"
echo -e "${GREEN}Environment:${NC} $NODE_ENV"
echo -e "${GREEN}RPC Port:${NC} $RPC_PORT"

# Create or update .env file
if [[ "$USING_EXISTING" = "false" ]]; then
  echo -e "${YELLOW}Creating new .env file...${NC}"
  cat > .env << EOF
# Bitcoin Configuration
BITCOIN_NETWORK="$BITCOIN_NETWORK"
BITCOIN_RPC_USER="$BITCOIN_RPC_USER"
BITCOIN_RPC_PASSWORD="$BITCOIN_RPC_PASSWORD"
BITCOIN_RPC_PORT="$RPC_PORT"

# Lightning Configuration
LIGHTNING_NETWORK="$LIGHTNING_NETWORK"
LIGHTNING_RPC_USER="$LIGHTNING_RPC_USER"
LIGHTNING_RPC_PASSWORD="$LIGHTNING_RPC_PASSWORD"

# Security
WALLET_ENCRYPTION_KEY="$WALLET_ENCRYPTION_KEY"

# Environment
NODE_ENV="$NODE_ENV"
SETUP_TIMESTAMP="$(date +%Y-%m-%d_%H:%M:%S)"
EOF
  echo -e "${GREEN}.env file created successfully!${NC}"
else
  echo -e "${YELLOW}Using existing .env file.${NC}"
fi

# Create necessary directories
mkdir -p data/bitcoin
mkdir -p data/lightning
mkdir -p logs

# Configure Bitcoin node with optimized settings
echo -e "${YELLOW}Configuring Bitcoin node...${NC}"
cat > data/bitcoin/bitcoin.conf << EOF
# RPC Settings
rpcuser="$BITCOIN_RPC_USER"
rpcpassword="$BITCOIN_RPC_PASSWORD"
$BITCOIN_NETWORK="1"
server="1"
rpcallowip="0.0.0.0/0"
rpcbind="0.0.0.0"
rpcport="$RPC_PORT"

# Performance Settings
dbcache="150"
maxmempool="300"
maxconnections="40"
prune="0"
txindex="1"

# Security Settings
disablewallet="0"
walletnotify="echo %s >> /home/bitcoin/logs/wallet_notify.log"

# Logging
debug="1"
logips="1"
logtimestamps="1"
EOF

# Configure Lightning node with optimized settings
echo -e "${YELLOW}Configuring Lightning node...${NC}"
cat > data/lightning/lightning.conf << EOF
# RPC Settings
rpcuser="$LIGHTNING_RPC_USER"
rpcpassword="$LIGHTNING_RPC_PASSWORD"
network="$LIGHTNING_NETWORK"
bitcoin-rpcuser="$BITCOIN_RPC_USER"
bitcoin-rpcpassword="$BITCOIN_RPC_PASSWORD"
bitcoin-rpcport="$RPC_PORT"

# Performance Settings
minchansize="20000"
max-pending-channels="10"
max-cltv-expiry="2016"

# Logging
debuglevel="info"
logdir="/root/logs"

# Security
bitcoin.active="true"
bitcoin.$BITCOIN_NETWORK="true"
bitcoin.node="bitcoind"
autopilot.active="false"
EOF

# Create a helper script for common commands
echo -e "${YELLOW}Creating helper script...${NC}"
cat > bitcoin-cli-helper.sh << EOF
#!/bin/bash

# Helper script for common bitcoin-cli commands
source .env

# Function to run bitcoin-cli command
bitcoin_cli() {
  docker exec bitcoin-node bitcoin-cli -rpcuser="\$BITCOIN_RPC_USER" -rpcpassword="\$BITCOIN_RPC_PASSWORD" "\$@"
}

# Function to run lightning-cli command
lightning_cli() {
  docker exec lightning-node lncli --rpcuser="\$LIGHTNING_RPC_USER" --rpcpassword="\$LIGHTNING_RPC_PASSWORD" "\$@"
}

case "\$1" in
  "info")
    bitcoin_cli getblockchaininfo
    ;;
  "mine")
    if [ -z "\$2" ]; then
      COUNT="1"
    else
      COUNT="\$2"
    fi
    ADDRESS="\$(bitcoin_cli getnewaddress)"
    bitcoin_cli generatetoaddress "\$COUNT" "\$ADDRESS"
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
    echo "Usage: \$0 {info|mine [blocks]|balance|ln-info|ln-balance|start|stop|logs}"
    exit 1
    ;;
esac
EOF

chmod +x bitcoin-cli-helper.sh

echo -e "${GREEN}Configuration complete!${NC}"
echo -e "${YELLOW}Run 'docker-compose up -d' to start the services${NC}"
echo -e "${YELLOW}Use './bitcoin-cli-helper.sh' for common commands${NC}"

# Make this script executable
chmod +x "${0}"