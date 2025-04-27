# Rust Bitcoin & Lightning Development Environment

This is a Rust-based development environment for interacting with Bitcoin and Lightning Network nodes using Docker. It's designed to complement the Java Lightning wallet project.

## Overview

This project includes:

1. **Docker Compose setup** - Configures Bitcoin Core and LND containers
2. **Rust client application** - Provides a CLI to interact with both Bitcoin and Lightning nodes

## Prerequisites

- Docker and Docker Compose
- Rust toolchain (if building locally outside Docker)

## Getting Started

1. Start the environment:

   ```bash
   cd bitcoin-lightning-dev
   ./setup.sh
   docker-compose up -d
   ```

2. Use the Rust client (inside Docker):

   ```bash
   docker exec -it bitcoin-rust-dev bash
   cargo build
   cargo run -- bitcoin getinfo
   ```

3. Alternatively, use the Rust client locally:
   ```bash
   cd rust-bitcoin-client
   cargo build
   cargo run -- bitcoin getinfo
   ```

## Available Commands

### Bitcoin Commands

- Get blockchain info:

  ```bash
  cargo run -- bitcoin getinfo
  ```

- Generate new blocks (testnet/regtest only):

  ```bash
  cargo run -- bitcoin generate --blocks 10
  ```

- Get wallet balance:

  ```bash
  cargo run -- bitcoin getbalance
  ```

- Get a new address:

  ```bash
  cargo run -- bitcoin getnewaddress
  ```

- Send Bitcoin to an address:
  ```bash
  cargo run -- bitcoin sendtoaddress --address <ADDRESS> --amount 0.001
  ```

### Lightning Commands

- Get node info:

  ```bash
  cargo run -- lightning getinfo
  ```

- Create a new invoice:

  ```bash
  cargo run -- lightning createinvoice --amount-sats 50000 --memo "Test payment"
  ```

- List all invoices:
  ```bash
  cargo run -- lightning listinvoices
  ```

## Integration with Java Project

This Rust client can be used alongside your Java Lightning wallet application for development and testing. The Docker-based setup ensures consistency across different development environments.

## Notes

- The Lightning implementation uses REST API for simplicity. In a production environment, you would use the gRPC API with proper authentication via macaroons.
- For debugging, check the logs in the `logs/` directory.
