#!/bin/bash

# Step 1: Build images
./build-images.sh "$@"

source .env

# Step 3: Deploy with Helm
echo helm upgrade --install sentrius ./sentrius-chart --set sentrius.image.tag=${SENTRIUS_VERSION} --set ssh.image.tag=${SENTRIUS_SSH_VERSION} --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION}
helm upgrade --install sentrius ./sentrius-chart --set sentrius.image.tag=${SENTRIUS_VERSION} --set ssh.image.tag=${SENTRIUS_SSH_VERSION} --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION}
