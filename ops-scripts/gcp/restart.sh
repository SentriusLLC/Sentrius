#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.gcp.env

# GCP Container Registry
GCP_REGISTRY="us-central1-docker.pkg.dev/sentrius-project/sentrius-repo"

echo "Restarting all deployments in namespace ${NAMESPACE}..."
kubectl scale deployment --all --replicas=1 -n ${NAMESPACE}

echo "Upgrading Sentrius deployment with latest configuration..."
helm upgrade --install sentrius ./sentrius-chart --namespace ${NAMESPACE} \
    --set tenant=${NAMESPACE} \
    --set environment=gke \
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
    --set rdpproxy.image.tag=${RDPPROXY_VERSION:-1.0.0} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }

echo "✅ Restart complete!"
