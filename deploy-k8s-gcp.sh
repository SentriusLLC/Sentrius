#!/bin/bash

# Step 1: Build images
./build-images-gcp.sh "$@"

source .gcp.env

# Step 3: Deploy with Helm

# Deploy to GKE using Helm
echo "Deploying to GKE using Helm..."

echo helm upgrade --install sentrius ./sentrius-gcp-chart \
         --namespace sentrius \
         --set sentrius.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius \
         --set sentrius.image.tag=$SENTRIUS_VERSION \
         --set ssh.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-ssh \
         --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
         --set keycloak.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-keycloak \
         --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
         --set sentriusagent.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-agent \
         --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }

helm upgrade --install sentrius ./sentrius-gcp-chart \
    --namespace sentrius \
    --set sentrius.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius \
    --set sentrius.image.tag=$SENTRIUS_VERSION \
    --set ssh.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-ssh \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set keycloak.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-keycloak \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set sentriusagent.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-agent \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }

echo "Deployment completed successfully."
