# Lightning Network Wallet

A Java-based GUI wallet application for interacting with a local Lightning Network node.

## Features

- View your Lightning node information and wallet balance
- Create and manage Lightning invoices with QR codes
- Pay Lightning invoices
- View transaction history
- Connect to your local Lightning Network node

## Prerequisites

- Java 11 or higher
- Maven
- A running Lightning Network node (e.g., LND, c-lightning, or Eclair)

## Building the Application

To build the application, run:

```bash
mvn clean package
```

This will create a JAR file in the `target` directory.

## Running the Application

To run the application, use:

```bash
java -jar target/lightning-network-app-1.0.0.jar
```

Or simply use Maven:

```bash
mvn javafx:run
```

## Configuration

The application is configured to connect to a local Lightning Network node. You can modify the connection settings in the `src/main/resources/lightning-config.properties` file:

```properties
# Lightning Network node connection details
host=localhost
port=8080

# Path to TLS certificate (leave empty to use insecure connection for development)
tls.cert.path=

# Wallet options
refresh.interval=30
```

## Development

This project is developed using:

- Java 11
- JavaFX for the GUI
- Maven for build automation
- Various libraries for Lightning Network communication

## License

This project is licensed under the MIT License - see the LICENSE file for details.
