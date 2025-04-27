#!/bin/bash

# Run the Lightning Network Wallet application
# This script resolves module conflicts for JavaFX on macOS

# Change to the project directory
cd "$(dirname "$0")"

# Default mode is maven
RUN_MODE="maven"

# Parse arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -j|--jar) RUN_MODE="jar"; shift ;;
        -h|--help) 
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  -j, --jar     Run directly from built JAR file"
            echo "  -h, --help    Show this help message"
            exit 0 ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
done

# Build the project if needed
mvn clean package -DskipTests

if [ "$RUN_MODE" = "jar" ]; then
    echo "Running from JAR file with macOS compatibility options..."
    # Use specific JVM arguments to fix the tracking rectangle issue on macOS
    java \
      --add-opens=java.base/java.lang=ALL-UNNAMED \
      --add-exports=java.base/java.lang=ALL-UNNAMED \
      --add-opens=java.base/java.nio=ALL-UNNAMED \
      --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED \
      --add-opens=javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED \
      --add-opens=javafx.graphics/com.sun.javafx.tk.quantum=ALL-UNNAMED \
      --add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
      --add-opens=javafx.graphics/com.sun.glass.ui.mac=ALL-UNNAMED \
      -Dglass.disableThreadChecks=true \
      -jar target/lightning-network-app-1.0.0.jar
else
    echo "Running using Maven JavaFX plugin..."
    # Use the JavaFX Maven plugin to run the application properly
    mvn javafx:run
fi