#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)


source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.env

TENANT=dev
if [[ -z "$TENANT" ]]; then
    echo "Must provide first argument for tenant name" 1>&2
    exit 1
fi

# Check if namespace exists
kubectl get namespace ${TENANT} >/dev/null 2>&1
if [[ $? -ne 0 ]]; then
    echo "Namespace ${TENANT} does not exist. Creating..."
    kubectl create namespace ${TENANT} || { echo "Failed to create namespace ${TENANT}"; exit 1; }
fi
#    --set sentrius-ssh.image.pullPolicy="Never" \
#    --set sentrius-keycloak.image.pullPolicy="Never" \
#    --set sentrius-bad-ssh.image.pullPolicy="Never" \


helm upgrade --install sentrius ./sentrius-gcp-chart --namespace ${TENANT} \
    --set tenant=${TENANT} \
    --set subdomain="localhost" \
    --set keycloakSubdomain="sentrius-keycloak:8081" \
    --set keycloakDomain="http://sentrius-keycloak:8081" \
    --set sentriusDomain="http://sentrius:8080" \
    --set sentrius.image.repository="sentrius" \
    --set sentrius.image.pullPolicy="Never" \
    --set ssh.image.pullPolicy="Never" \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }

