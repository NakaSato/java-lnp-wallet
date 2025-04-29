#!/bin/bash

# Run the Lightning Network Wallet application
# This script has been updated to run the application using Swing UI

# Change to the project directory
cd "$(dirname "$0")"

# Default mode is direct
RUN_MODE="direct"

# Parse arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -m|--maven) RUN_MODE="maven"; shift ;;
        -h|--help) 
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  -m, --maven    Run using Maven"
            echo "  -h, --help     Show this help message"
            exit 0 ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
done

# Build the project if needed
mvn clean package -DskipTests

if [ "$RUN_MODE" = "maven" ]; then
    echo "Running using Maven..."
    mvn exec:java -Dexec.mainClass="com.lightning.AppLauncher"
else
    echo "Running directly from JAR file..."
    # Run directly from the JAR file (no special arguments needed with Swing)
    java -jar target/lightning-network-app-1.0.0.jar
fi