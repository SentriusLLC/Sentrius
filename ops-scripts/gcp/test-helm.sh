#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)


source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.gcp.env

TENANT=$1

if [[ -z "$TENANT" ]]; then
    echo "Must provide single argument for tenant name" 1>&2
    exit 1
fi

# Check if namespace exists
kubectl get namespace ${TENANT} >/dev/null 2>&1
if [[ $? -ne 0 ]]; then
    echo "Namespace ${TENANT} does not exist. Creating..."
    kubectl create namespace ${TENANT} || { echo "Failed to create namespace ${TENANT}"; exit 1; }
fi



helm template ${TENANT} ./sentrius-gcp-chart/ --values sentrius-gcp-chart/values.yaml \
    --set tenant=${TENANT} \
    --set subdomain=${TENANT}.sentrius.cloud \
    --set sentrius.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set ssh.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-ssh \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set keycloak.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-keycloak \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set sentriusagent.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-agent \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }
