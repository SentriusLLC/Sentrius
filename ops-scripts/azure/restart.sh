#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.azure.env

# Azure Container Registry
AZURE_REGISTRY="${AZURE_REGISTRY:-sentriusacr.azurecr.io}"

TENANT="${1:-${NAMESPACE}}"

if [[ -z "$TENANT" ]]; then
    echo "Usage: $0 <tenant-name>" 1>&2
    echo "Example: $0 production"
    exit 1
fi

echo "Restarting all deployments in namespace ${TENANT}..."
kubectl scale deployment --all --replicas=1 -n ${TENANT}

echo "Upgrading Sentrius deployment with latest configuration..."
helm upgrade --install sentrius ./sentrius-chart --namespace ${TENANT} \
    --set tenant=${TENANT} \
    --set environment=aks \
    --set sentrius.image.repository=${AZURE_REGISTRY}/sentrius \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set ssh.image.repository=${AZURE_REGISTRY}/sentrius-ssh \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set keycloak.image.repository=${AZURE_REGISTRY}/sentrius-keycloak \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set sentriusagent.image.repository=${AZURE_REGISTRY}/sentrius-agent \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} \
    --set sentriusaiagent.image.repository=${AZURE_REGISTRY}/sentrius-ai-agent \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set integrationproxy.image.repository=${AZURE_REGISTRY}/sentrius-integration-proxy \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set agentproxy.image.repository=${AZURE_REGISTRY}/sentrius-agent-proxy \
    --set agentproxy.image.tag=${AGENTPROXY_VERSION:-1.0.0} \
    --set launcherservice.image.repository=${AZURE_REGISTRY}/sentrius-launcher-service \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --set sshproxy.image.repository=${AZURE_REGISTRY}/sentrius-ssh-proxy \
    --set sshproxy.image.tag=${SSHPROXY_VERSION:-1.0.0} \
    --set rdpproxy.image.repository=${AZURE_REGISTRY}/sentrius-rdp-proxy \
    --set rdpproxy.image.tag=${RDPPROXY_VERSION:-1.0.0} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }

echo "✅ Restart complete!"
