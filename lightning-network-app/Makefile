# Makefile for Lightning Network Wallet

.PHONY: clean compile package run

# Default target
all: package

# Clean the project
clean:
	./mvnw clean

# Compile the project
compile:
	./mvnw compile

# Package the project into a JAR file
package:
	./mvnw package

# Run the application
run:
	./mvnw javafx:run

# Run tests
test:
	./mvnw test

# Install Maven wrapper if it doesn't exist
install-wrapper:
	mvn -N io.takari:maven:wrapper

# Help target
help:
	@echo "Lightning Network Wallet Makefile"
	@echo ""
	@echo "Available targets:"
	@echo "  all         - Default target, packages the application"
	@echo "  clean       - Clean the project"
	@echo "  compile     - Compile the project"
	@echo "  package     - Package the project into a JAR file"
	@echo "  run         - Run the application"
	@echo "  test        - Run tests"
	@echo "  install-wrapper - Install Maven wrapper"
	@echo "  help        - Show this help message"
