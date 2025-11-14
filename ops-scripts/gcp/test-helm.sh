#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.gcp.env

TENANT=${1:-test-tenant}

# GCP Container Registry
GCP_REGISTRY="us-central1-docker.pkg.dev/sentrius-project/sentrius-repo"

echo "Testing Helm chart rendering for tenant: ${TENANT}"
echo "========================================"
echo ""

echo "Testing main sentrius-chart..."
helm template ${TENANT} ./sentrius-chart/ \
    --set tenant=${TENANT} \
    --set environment=gke \
    --set subdomain=${TENANT}.sentrius.cloud \
    --set keycloakSubdomain=keycloak.${TENANT}.sentrius.cloud \
    --set agentproxySubdomain=agentproxy.${TENANT}.sentrius.cloud \
    --set rdpproxySubdomain=rdpproxy.${TENANT}.sentrius.cloud \
    --set sentrius.image.repository=${GCP_REGISTRY}/sentrius \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set ssh.image.repository=${GCP_REGISTRY}/sentrius-ssh \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set keycloak.image.repository=${GCP_REGISTRY}/sentrius-keycloak \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set sentriusagent.image.repository=${GCP_REGISTRY}/sentrius-agent \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} \
    --set sentriusaiagent.image.repository=${GCP_REGISTRY}/sentrius-ai-agent \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set integrationproxy.image.repository=${GCP_REGISTRY}/sentrius-integration-proxy \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set agentproxy.image.repository=${GCP_REGISTRY}/sentrius-agent-proxy \
    --set agentproxy.image.tag=${AGENTPROXY_VERSION:-1.0.0} \
    --set launcherservice.image.repository=${GCP_REGISTRY}/sentrius-launcher-service \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --set sshproxy.image.repository=${GCP_REGISTRY}/sentrius-ssh-proxy \
    --set sshproxy.image.tag=${SSHPROXY_VERSION:-1.0.0} \
    --set rdpproxy.image.repository=${GCP_REGISTRY}/sentrius-rdp-proxy \
    --set rdpproxy.image.tag=${RDPPROXY_VERSION:-1.0.0} \
    --dry-run || { echo "Failed to render sentrius-chart"; exit 1; }

echo ""
echo "Testing sentrius-chart-launcher..."
helm template ${TENANT}-agents ./sentrius-chart-launcher/ \
    --set tenant=${TENANT}-agents \
    --set sentriusNamespace=${TENANT} \
    --set sentrius.image.repository=${GCP_REGISTRY}/sentrius \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set launcherservice.image.repository=${GCP_REGISTRY}/sentrius-launcher-service \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --dry-run || { echo "Failed to render sentrius-chart-launcher"; exit 1; }

echo ""
echo "✅ All Helm chart templates rendered successfully!"
