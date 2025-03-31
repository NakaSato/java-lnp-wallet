# Detect the operating system
OS := $(shell uname -s)

# Default target
all: install

# Install dependencies based on the OS
install:
ifeq ($(OS), Linux)
	@echo "Installing dependencies for Linux..."
	# Add Linux-specific installation commands here
	sudo apt-get update
	sudo apt-get install -y openjdk-11-jdk maven
endif
ifeq ($(OS), Darwin)
	@echo "Installing dependencies for macOS..."
	# Add macOS-specific installation commands here
	brew update
	brew install openjdk@11 maven
endif
ifeq ($(OS), Windows_NT)
	@echo "Installing dependencies for Windows..."
	# Add Windows-specific installation commands here
	choco install openjdk11 maven -y
endif

# Autocomplete setup (example for bash)
autocomplete:
	@echo "Setting up autocomplete..."
	# Add autocomplete setup commands here
	@echo "source <(mvn completion bash)" >> ~/.bashrc || true
	@echo "source <(mvn completion bash)" >> ~/.zshrc || true

# Clean target
clean:
	@echo "Cleaning up..."
	# Add cleanup commands here
	rm -rf target

# Help target
help:
	@echo "Available targets:"
	@echo "  all          - Install dependencies"
	@echo "  install      - Install dependencies"
	@echo "  autocomplete - Set up autocomplete for Maven"
	@echo "  clean        - Clean up build artifacts"
	@echo "  help         - Show this help message"
