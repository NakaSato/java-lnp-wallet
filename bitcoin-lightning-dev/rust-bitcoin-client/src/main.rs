use anyhow::Result;
use bitcoincore_rpc::{Auth, Client, RpcApi};
use clap::{Parser, Subcommand};
use dotenv::dotenv;
use serde::{Deserialize, Serialize};
use std::env;

// CLI Commands and Arguments
#[derive(Parser)]
#[command(author, version, about, long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Bitcoin commands
    Bitcoin {
        #[command(subcommand)]
        action: BitcoinCommands,
    },
    /// Lightning commands
    Lightning {
        #[command(subcommand)]
        action: LightningCommands,
    },
}

#[derive(Subcommand)]
enum BitcoinCommands {
    /// Get blockchain info
    #[command(name = "getinfo")]
    GetInfo,
    /// Generate new blocks (test network only)
    #[command(name = "generate")]
    Generate { blocks: u64 },
    /// Get wallet balance
    #[command(name = "getbalance")]
    GetBalance,
    /// Get a new address
    #[command(name = "getnewaddress")]
    GetNewAddress,
    /// Send Bitcoin to an address
    #[command(name = "sendtoaddress")]
    SendToAddress { address: String, amount: f64 },
}

#[derive(Subcommand)]
enum LightningCommands {
    /// Get node info
    #[command(name = "getinfo")]
    GetInfo,
    /// Create a new invoice
    #[command(name = "createinvoice")]
    CreateInvoice { amount_sats: u64, memo: String },
    /// List all invoices
    #[command(name = "listinvoices")]
    ListInvoices,
    /// Connect to another lightning node
    #[command(name = "connect")]
    Connect { node_pubkey: String, host: String },
    /// List all peers
    #[command(name = "listpeers")]
    ListPeers,
}

// Response types for LND
#[derive(Debug, Serialize, Deserialize)]
struct LndInfoResponse {
    identity_pubkey: String,
    alias: String,
    num_active_channels: u32,
    num_pending_channels: u32,
    num_peers: u32,
    block_height: u32,
    synced_to_chain: bool,
    testnet: bool,
    version: String,
}

#[derive(Debug, Serialize, Deserialize)]
struct LndInvoiceResponse {
    r_hash: String,
    payment_request: String,
    add_index: u64,
    payment_addr: String,
}

#[derive(Debug, Serialize, Deserialize)]
struct LndInvoicesResponse {
    invoices: Vec<LndInvoice>,
}

#[derive(Debug, Serialize, Deserialize)]
struct LndInvoice {
    memo: String,
    r_preimage: String,
    r_hash: String,
    value: u64,
    value_msat: u64,
    settled: bool,
    creation_date: u64,
    settle_date: u64,
    payment_request: String,
    expiry: u64,
    amt_paid: u64,
    amt_paid_msat: u64,
}

// Bitcoin RPC client setup
fn get_bitcoin_client() -> Result<Client> {
    let rpc_user = env::var("BITCOIN_RPC_USER").expect("BITCOIN_RPC_USER not set");
    let rpc_password = env::var("BITCOIN_RPC_PASSWORD").expect("BITCOIN_RPC_PASSWORD not set");
    let rpc_host = env::var("BITCOIN_RPC_HOST").unwrap_or_else(|_| "localhost".to_string());
    let rpc_url = format!("http://{}:8332", rpc_host);
    
    let auth = Auth::UserPass(rpc_user, rpc_password);
    let client = Client::new(&rpc_url, auth)?;
    Ok(client)
}

// LND REST client for the Lightning Network
async fn get_lnd_info() -> Result<LndInfoResponse> {
    let base_url = env::var("LIGHTNING_RPC_HOST").unwrap_or_else(|_| "localhost".to_string());
    let url = format!("https://{}:8080/v1/getinfo", base_url);
    
    // In a real application, you'd use proper TLS with macaroons for authentication
    // This is a simplified example for demonstration
    let response = reqwest::Client::builder()
        .danger_accept_invalid_certs(true)  // Only for development!
        .build()?
        .get(&url)
        .send()
        .await?;
    
    let info: LndInfoResponse = response.json().await?;
    Ok(info)
}

async fn create_lnd_invoice(amount_sats: u64, memo: &str) -> Result<LndInvoiceResponse> {
    let base_url = env::var("LIGHTNING_RPC_HOST").unwrap_or_else(|_| "localhost".to_string());
    let url = format!("https://{}:8080/v1/invoices", base_url);
    
    let payload = serde_json::json!({
        "value": amount_sats,
        "memo": memo,
    });
    
    let response = reqwest::Client::builder()
        .danger_accept_invalid_certs(true)  // Only for development!
        .build()?
        .post(&url)
        .json(&payload)
        .send()
        .await?;
    
    let invoice: LndInvoiceResponse = response.json().await?;
    Ok(invoice)
}

async fn list_lnd_invoices() -> Result<LndInvoicesResponse> {
    let base_url = env::var("LIGHTNING_RPC_HOST").unwrap_or_else(|_| "localhost".to_string());
    let url = format!("https://{}:8080/v1/invoices", base_url);
    
    let response = reqwest::Client::builder()
        .danger_accept_invalid_certs(true)  // Only for development!
        .build()?
        .get(&url)
        .send()
        .await?;
    
    let invoices: LndInvoicesResponse = response.json().await?;
    Ok(invoices)
}

// Main function
#[tokio::main]
async fn main() -> Result<()> {
    // Initialize environment
    dotenv().ok();
    env_logger::init();
    
    let cli = Cli::parse();
    
    // Process commands
    match &cli.command {
        Commands::Bitcoin { action } => match action {
            BitcoinCommands::GetInfo => {
                let client = get_bitcoin_client()?;
                let info = client.get_blockchain_info()?;
                println!("Bitcoin Node Info:");
                println!("Chain: {}", info.chain);
                println!("Blocks: {}", info.blocks);
                println!("Headers: {}", info.headers);
                println!("Best Block Hash: {}", info.best_block_hash);
                println!("Difficulty: {}", info.difficulty);
                println!("Verification Progress: {}", info.verification_progress);
            }
            BitcoinCommands::Generate { blocks } => {
                let client = get_bitcoin_client()?;
                
                // Use the generate method which doesn't require an address parameter
                // This works for regtest/testnet networks
                let block_hashes = client.generate(*blocks, Some(1))?;
                println!("Generated {} blocks:", block_hashes.len());
                for (i, hash) in block_hashes.iter().enumerate() {
                    println!("Block {}: {}", i + 1, hash);
                }
            }
            BitcoinCommands::GetBalance => {
                let client = get_bitcoin_client()?;
                let balance = client.get_balance(None, None)?;
                println!("Wallet Balance: {} BTC", balance.to_btc());
            }
            BitcoinCommands::GetNewAddress => {
                let client = get_bitcoin_client()?;
                let address = client.get_new_address(None, None)?;
                // Use debug formatting to display the address
                println!("New Address: {:?}", address);
            }
            BitcoinCommands::SendToAddress { address, amount } => {
                let client = get_bitcoin_client()?;
                let btc_amount = bitcoin::Amount::from_btc(*amount)?;
                
                // Use the direct RPC call approach which properly handles address parsing
                let params = [
                    serde_json::json!(address),
                    serde_json::json!(btc_amount.to_btc()),
                ];
                let txid = client.call::<bitcoin::Txid>("sendtoaddress", &params)?;
                println!("Transaction sent! TXID: {}", txid);
            }
        },
        Commands::Lightning { action } => match action {
            LightningCommands::GetInfo => {
                let info = get_lnd_info().await?;
                println!("Lightning Node Info:");
                println!("Pubkey: {}", info.identity_pubkey);
                println!("Alias: {}", info.alias);
                println!("Active Channels: {}", info.num_active_channels);
                println!("Pending Channels: {}", info.num_pending_channels);
                println!("Peers: {}", info.num_peers);
                println!("Block Height: {}", info.block_height);
                println!("Synced to Chain: {}", info.synced_to_chain);
                println!("Testnet: {}", info.testnet);
                println!("Version: {}", info.version);
            }
            LightningCommands::CreateInvoice { amount_sats, memo } => {
                let invoice = create_lnd_invoice(*amount_sats, memo).await?;
                println!("Invoice created:");
                println!("Payment Request: {}", invoice.payment_request);
                println!("R Hash: {}", invoice.r_hash);
            }
            LightningCommands::ListInvoices => {
                let invoices = list_lnd_invoices().await?;
                println!("Invoices:");
                for (i, invoice) in invoices.invoices.iter().enumerate() {
                    println!("Invoice #{}:", i + 1);
                    println!("  Memo: {}", invoice.memo);
                    println!("  Amount: {} sats", invoice.value);
                    println!("  Settled: {}", invoice.settled);
                    println!("  Payment Request: {}", invoice.payment_request);
                    println!();
                }
            }
            LightningCommands::Connect { node_pubkey, host } => {
                println!("Connect to {} at {} not implemented yet", node_pubkey, host);
                // In a real application, you would implement the LND gRPC client for this
            }
            LightningCommands::ListPeers => {
                println!("List peers not implemented yet");
                // In a real application, you would implement the LND gRPC client for this
            }
        },
    }
    
    Ok(())
}
