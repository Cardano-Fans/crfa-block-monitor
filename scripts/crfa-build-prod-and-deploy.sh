#!/bin/bash

set -e

# Configuration
HOST="10.0.0.1"
CONTAINER_NAME="block-monitor-backend"
DEPLOY_DIR="/opt/block-monitor-backend"
SERVER_HOST="ubuntu@$HOST"

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

# Step 1: Clean up local files
log_info "Cleaning up local files..."
rm -f block-monitor-backend-latest.tar
rm -f block-monitor-backend-latest.tar.gz

# Step 2: Build native container image
log_info "Building native container image..."
docker build -t block-monitor-backend:latest -f Dockerfile.native .

# Step 3: Save and compress image
log_info "Saving and compressing image..."
docker save -o block-monitor-backend-latest.tar block-monitor-backend:latest
gzip block-monitor-backend-latest.tar

# Step 4: Clean up remote files
log_info "Cleaning up remote files..."
ssh "$SERVER_HOST" "rm -f block-monitor-backend-latest.tar ; rm -f block-monitor-backend-latest.tar.gz"

# Step 5: Upload to server
log_info "Uploading container image to server..."
scp block-monitor-backend-latest.tar.gz "$SERVER_HOST:~"

# Step 6: Deploy on remote machine
log_info "Deploying on remote machine..."
ssh "$SERVER_HOST" << EOF
    set -e
    
    # Extract and load new image
    echo "Extracting and loading container image..."
    gunzip -c block-monitor-backend-latest.tar.gz | sudo -u cardano podman load && 
    
    # Stop and remove old container
    echo "Stopping and removing old container..."
    if sudo -u cardano podman ps -a --format "{{.Names}}" | grep -q "^$CONTAINER_NAME\$"; then
        sudo -u cardano podman stop $CONTAINER_NAME || true
        sudo -u cardano podman rm $CONTAINER_NAME || true
    fi &&
    
    # Ensure deploy directory exists and is owned by cardano
    echo "Setting up deploy directory..."
    sudo mkdir -p $DEPLOY_DIR &&
    sudo chown cardano:cardano $DEPLOY_DIR &&
    
    # Start new container with restart policy
    echo "Starting new container..."
    sudo -u cardano podman run -d \\
        --name $CONTAINER_NAME \\
        --restart unless-stopped \\
        -p 8080:8080 \\
        --network host \\
        -v $DEPLOY_DIR/application-prod.yml:/work/application-prod.yml:ro \\
        --entrypoint='["./application", "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.config.locations=/work/application-prod.yml"]' \\
        block-monitor-backend:latest &&
    
    # Clean up remote tar file
    echo "Cleaning up remote files..."
    rm -f block-monitor-backend-latest.tar
    
    echo "Container deployment completed successfully!"
EOF

# Step 7: Clean up local files after successful deployment
if [ $? -eq 0 ]; then
    log_info "Deployment successful! Cleaning up local files..."
    rm -f block-monitor-backend-latest.tar.gz
else
    log_error "Deployment failed! Local tar.gz file preserved for debugging"
    exit 1
fi

log_info "Deployment script completed!"