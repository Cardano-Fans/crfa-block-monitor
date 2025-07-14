#!/bin/bash

set -e

# Configuration
SERVER_HOST="ubuntu@10.0.0.1"
INSTALL_DIR="/opt/block-monitor-backend"
SERVICE_NAME="block-monitor-backend"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Step 1: Build native image
log_info "Building native image..."
./gradlew build -Dquarkus.package.type=native -x test

# Step 2: Find the executable in build directory
log_info "Finding executable in build directory..."
EXECUTABLE=$(find build -name "*-runner" -type f | head -1)

if [ -z "$EXECUTABLE" ]; then
    log_error "No executable found in build directory"
    exit 1
fi

log_info "Found executable: $EXECUTABLE"

# Step 3: Upload to server
log_info "Uploading executable to server..."
scp "$EXECUTABLE" "$SERVER_HOST:~/"

# Get the basename for the uploaded file
UPLOADED_FILE=$(basename "$EXECUTABLE")

# Step 4: Install on server
log_info "Installing on server..."
ssh "$SERVER_HOST" << EOF
    set -e
    
    # Check what executable currently exists
    CURRENT_EXECUTABLE=\$(sudo -u cardano find "$INSTALL_DIR" -name "*-runner" -o -name "block-monitor-backend" -type f | head -1)
    
    if [ -z "\$CURRENT_EXECUTABLE" ]; then
        echo "No existing executable found in $INSTALL_DIR"
        exit 1
    fi
    
    CURRENT_NAME=\$(basename "\$CURRENT_EXECUTABLE")
    echo "Current executable: \$CURRENT_NAME"
    
    # Create backup
    echo "Creating backup of existing executable..."
    sudo -u cardano cp "\$CURRENT_EXECUTABLE" "\$CURRENT_EXECUTABLE.backup.\$(date +%Y%m%d_%H%M%S)"
    
    # Stop service
    echo "Stopping $SERVICE_NAME service..."
    sudo systemctl stop "$SERVICE_NAME"
    
    # Install new executable
    echo "Installing new executable..."
    sudo cp "\$HOME/$UPLOADED_FILE" "$INSTALL_DIR/\$CURRENT_NAME"
    sudo chown cardano:cardano "$INSTALL_DIR/\$CURRENT_NAME"
    sudo chmod +x "$INSTALL_DIR/\$CURRENT_NAME"
    
    # Start service
    echo "Starting $SERVICE_NAME service..."
    sudo systemctl start "$SERVICE_NAME"
    
    # Check service status
    echo "Checking service status..."
    sudo systemctl status "$SERVICE_NAME" --no-pager
    
    # Cleanup uploaded file
    rm "\$HOME/$UPLOADED_FILE"
    
    echo "Deployment completed successfully!"
EOF

log_info "Deployment script completed!"