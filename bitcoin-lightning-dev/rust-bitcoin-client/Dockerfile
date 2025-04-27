# Development Dockerfile for rust-bitcoin-client
FROM rust:1.86-alpine

# Install development dependencies
RUN apk add --no-cache \
    musl-dev \
    openssl-dev \
    pkgconfig \
    git \
    make \
    bash \
    curl

# Create and set working directory
WORKDIR /app

# We don't copy any files here since the docker-compose.yml
# mounts the local directory as a volume

# Add cargo binary directory to PATH
ENV PATH="/usr/local/cargo/bin:${PATH}"

# Set environment for development
ENV RUST_LOG=debug
ENV RUST_BACKTRACE=1

# Command to keep container running
CMD ["sh", "-c", "tail -f /dev/null"]