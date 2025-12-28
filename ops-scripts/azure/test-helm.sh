#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.azure.env

TENANT=${1:-test-tenant}
DOMAIN_NAME="trustpolicy.ai"  # Default domain for Azure

echo "======================================"
echo "🧪 Testing Helm Chart Rendering"
echo "======================================"
echo "Tenant: ${TENANT}"
echo "Domain: ${DOMAIN_NAME}"
echo ""

# Azure Container Registry
AZURE_REGISTRY="${AZURE_REGISTRY:-sentriusacr.azurecr.io}"

# Test render sentrius-chart
echo "📦 Testing sentrius-chart..."
helm template sentrius ./sentrius-chart \
    --namespace ${TENANT} \
    --set tenant=${TENANT} \
    --set environment=aks \
    --set ingress.class="azure-application-gateway" \
    --set subdomain="${TENANT}.${DOMAIN_NAME}" \
    --set agentproxySubdomain="agentproxy.${TENANT}.${DOMAIN_NAME}" \
    --set keycloakSubdomain="keycloak.${TENANT}.${DOMAIN_NAME}" \
    --set rdpproxySubdomain="rdpproxy.${TENANT}.${DOMAIN_NAME}" \
    --set keycloakHostname="keycloak.${TENANT}.${DOMAIN_NAME}" \
    --set keycloakDomain="https://keycloak.${TENANT}.${DOMAIN_NAME}" \
    --set keycloakInternalDomain="https://keycloak.${TENANT}.${DOMAIN_NAME}" \
    --set sentriusDomain="https://${TENANT}.${DOMAIN_NAME}" \
    --set agentproxyDomain="https://agentproxy.${TENANT}.${DOMAIN_NAME}" \
    --set rdpproxyDomain="https://rdpproxy.${TENANT}.${DOMAIN_NAME}" \
    --set certificates.enabled=true \
    --set ingress.tlsEnabled=true \
    --set sentrius.image.repository="${AZURE_REGISTRY}/sentrius" \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set keycloak.image.repository="${AZURE_REGISTRY}/sentrius-keycloak" \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set ssh.image.repository="${AZURE_REGISTRY}/sentrius-ssh" \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set sentriusagent.image.repository="${AZURE_REGISTRY}/sentrius-agent" \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} \
    --set sentriusaiagent.image.repository="${AZURE_REGISTRY}/sentrius-ai-agent" \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set integrationproxy.image.repository="${AZURE_REGISTRY}/sentrius-integration-proxy" \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set agentproxy.image.repository="${AZURE_REGISTRY}/sentrius-agent-proxy" \
    --set agentproxy.image.tag=${AGENTPROXY_VERSION} \
    --set launcherservice.image.repository="${AZURE_REGISTRY}/sentrius-launcher-service" \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --set sshproxy.image.repository="${AZURE_REGISTRY}/sentrius-ssh-proxy" \
    --set sshproxy.image.tag=${SSHPROXY_VERSION} \
    --set rdpproxy.image.repository="${AZURE_REGISTRY}/sentrius-rdp-proxy" \
    --set rdpproxy.image.tag=${RDPPROXY_VERSION} \
    > /tmp/sentrius-chart-test.yaml

if [[ $? -eq 0 ]]; then
    echo "✅ sentrius-chart rendered successfully"
    echo "   Output saved to /tmp/sentrius-chart-test.yaml"
else
    echo "❌ sentrius-chart rendering failed"
    exit 1
fi

echo ""
echo "📦 Testing sentrius-chart-launcher..."
helm template sentrius-agents ./sentrius-chart-launcher \
    --namespace ${TENANT}-agents \
    --set tenant=${TENANT}-agents \
    --set baseRelease=sentrius \
    --set sentriusNamespace=${TENANT} \
    --set ingress.class="azure-application-gateway" \
    --set keycloakFQDN=sentrius-keycloak.${TENANT}.svc.cluster.local \
    --set sentriusFQDN=sentrius-sentrius.${TENANT}.svc.cluster.local \
    --set integrationproxyFQDN=sentrius-integrationproxy.${TENANT}.svc.cluster.local \
    --set agentproxyFQDN=sentrius-agentproxy.${TENANT}.svc.cluster.local \
    --set subdomain="${TENANT}.${DOMAIN_NAME}" \
    --set agentproxySubdomain="agentproxy.${TENANT}.${DOMAIN_NAME}" \
    --set keycloakSubdomain="keycloak.${TENANT}.${DOMAIN_NAME}" \
    --set keycloakHostname="keycloak.${TENANT}.${DOMAIN_NAME}" \
    --set keycloakDomain="https://keycloak.${TENANT}.${DOMAIN_NAME}" \
    --set keycloakInternalDomain="https://keycloak.${TENANT}.${DOMAIN_NAME}" \
    --set sentriusDomain="https://${TENANT}.${DOMAIN_NAME}" \
    --set agentproxyDomain="https://agentproxy.${TENANT}.${DOMAIN_NAME}" \
    --set sentrius.image.repository="${AZURE_REGISTRY}/sentrius" \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set keycloak.image.repository="${AZURE_REGISTRY}/sentrius-keycloak" \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set ssh.image.repository="${AZURE_REGISTRY}/sentrius-ssh" \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set sentriusagent.image.repository="${AZURE_REGISTRY}/sentrius-agent" \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} \
    --set sentriusaiagent.image.repository="${AZURE_REGISTRY}/sentrius-ai-agent" \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set integrationproxy.image.repository="${AZURE_REGISTRY}/sentrius-integration-proxy" \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set launcherservice.image.repository="${AZURE_REGISTRY}/sentrius-launcher-service" \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    > /tmp/sentrius-chart-launcher-test.yaml

if [[ $? -eq 0 ]]; then
    echo "✅ sentrius-chart-launcher rendered successfully"
    echo "   Output saved to /tmp/sentrius-chart-launcher-test.yaml"
else
    echo "❌ sentrius-chart-launcher rendering failed"
    exit 1
fi

echo ""
echo "======================================"
echo "✅ All Tests Passed!"
echo "======================================"
