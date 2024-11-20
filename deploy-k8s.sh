#!/bin/bash

# Step 1: Build images
./build-images-local.sh "$@"

source .env

# Step 3: Deploy with Helm
echo helm upgrade --install sentrius ./sentrius-chart --set sentrius.image.tag=${SENTRIUS_VERSION} --set ssh.image.tag=${SENTRIUS_SSH_VERSION} --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} -n default
helm upgrade --install sentrius ./sentrius-chart --set sentrius.image.tag=${SENTRIUS_VERSION} --set ssh.image.tag=${SENTRIUS_SSH_VERSION} --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} -n default
