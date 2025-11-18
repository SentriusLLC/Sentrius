#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../base/base.sh
source ${SCRIPT_DIR}/../../.gcp.env

# For GKE deployments, use versioned tags from .gcp.env
# Default to 'latest' if .gcp.env is not sourced or variables are not set
SENTRIUS_VERSION="${SENTRIUS_VERSION:-latest}"
SENTRIUS_SSH_VERSION="${SENTRIUS_SSH_VERSION:-latest}"
SENTRIUS_KEYCLOAK_VERSION="${SENTRIUS_KEYCLOAK_VERSION:-latest}"
SENTRIUS_AGENT_VERSION="${SENTRIUS_AGENT_VERSION:-latest}"
SENTRIUS_AI_AGENT_VERSION="${SENTRIUS_AI_AGENT_VERSION:-latest}"
LLMPROXY_VERSION="${LLMPROXY_VERSION:-latest}"
LAUNCHER_VERSION="${LAUNCHER_VERSION:-latest}"
AGENTPROXY_VERSION="${AGENTPROXY_VERSION:-latest}"
SSHPROXY_VERSION="${SSHPROXY_VERSION:-latest}"
RDPPROXY_VERSION="${RDPPROXY_VERSION:-latest}"
GITHUB_MCP_VERSION="${GITHUB_MCP_VERSION:-latest}"

TENANT=""
ENV_TARGET="gke"
CERTIFICATES_ENABLED="true"
INGRESS_TLS_ENABLED="true"
ENVIRONMENT="gke"
DEPLOY_ADMINER=${DEPLOY_ADMINER:-false}
ENABLE_RDP_CONTAINER=${ENABLE_RDP_CONTAINER:-false}

# GCP Container Registry
GCP_REGISTRY="us-central1-docker.pkg.dev/sentrius-project/sentrius-repo"

# Generate secrets using the shared script
(source ${SCRIPT_DIR}/../base/generate-secrets.sh)

GENERATED_ENV_PATH="${SCRIPT_DIR}/../../.generated.env"
if [[ -f "$GENERATED_ENV_PATH" ]]; then
    source "$GENERATED_ENV_PATH"
fi

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --tenant)
            TENANT="$2"
            shift 2
            ;;
        --no-tls)
            CERTIFICATES_ENABLED="false"
            INGRESS_TLS_ENABLED="false"
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 --tenant TENANT_NAME [--no-tls]"
            echo "  --tenant: Specify tenant name (required)"
            echo "  --no-tls: Disable TLS/SSL (not recommended for production)"
            exit 1
            ;;
    esac
done

if [[ -z "$TENANT" ]]; then
    echo "Must provide tenant name with --tenant" 1>&2
    echo "Usage: $0 --tenant TENANT_NAME [--no-tls]"
    exit 1
fi

# Configure domain settings for GKE
SUBDOMAIN="${TENANT}.sentrius.cloud"
APROXY_SUBDOMAIN="agentproxy.${TENANT}.sentrius.cloud"
KEYCLOAK_SUBDOMAIN="keycloak.${TENANT}.sentrius.cloud"
RDPPROXY_SUBDOMAIN="rdpproxy.${TENANT}.sentrius.cloud"
KEYCLOAK_HOSTNAME="${KEYCLOAK_SUBDOMAIN}"
KEYCLOAK_DOMAIN="https://${KEYCLOAK_SUBDOMAIN}"
KEYCLOAK_INTERNAL_DOMAIN="http://sentrius-keycloak:8081"
SENTRIUS_DOMAIN="https://${SUBDOMAIN}"
APROXY_DOMAIN="https://${APROXY_SUBDOMAIN}"
RDPPROXY_DOMAIN="https://${RDPPROXY_SUBDOMAIN}"

# Check if namespace exists
kubectl get namespace ${TENANT} >/dev/null 2>&1
if [[ $? -ne 0 ]]; then
    echo "Namespace ${TENANT} does not exist. Creating..."
    kubectl create namespace ${TENANT} || { echo "Failed to create namespace ${TENANT}"; exit 1; }
fi

kubectl get namespace ${TENANT}-agents >/dev/null 2>&1
if [[ $? -ne 0 ]]; then
    echo "Namespace ${TENANT}-agents does not exist. Creating..."
    kubectl create namespace ${TENANT}-agents || { echo "Failed to create namespace ${TENANT}-agents"; exit 1; }
fi

# Wait for admission webhooks to be ready (prevents validation failures during deployment)
echo "🔍 Checking for admission webhooks..."

# Check for ingress controller webhook
if kubectl get validatingwebhookconfigurations 2>/dev/null | grep -q "ingress"; then
    echo "⏳ Waiting for ingress admission webhook to be ready..."
    for i in {1..30}; do
        if kubectl get validatingwebhookconfigurations 2>/dev/null | grep -q "ingress.*admission"; then
            echo "✅ Ingress admission webhook is configured"
            sleep 2  # Brief pause to ensure webhook is fully operational
            break
        fi
        echo "Waiting for ingress webhook configuration... ($i/30)"
        sleep 2
    done
fi

# Check for cert-manager webhook (if TLS is enabled)
if [[ "$CERTIFICATES_ENABLED" == "true" ]]; then
    if kubectl get validatingwebhookconfigurations cert-manager-webhook >/dev/null 2>&1; then
        echo "⏳ Waiting for cert-manager webhook to be fully operational..."
        # Wait for cert-manager webhook pods to be ready
        if kubectl get pods -n cert-manager -l app.kubernetes.io/name=webhook >/dev/null 2>&1; then
            kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=webhook -n cert-manager --timeout=60s 2>/dev/null || echo "⚠️ cert-manager webhook may not be fully ready"
        fi
        echo "✅ cert-manager webhook check complete"
        sleep 2  # Brief pause to ensure webhook is fully operational
    fi
fi

# Generate Keycloak DB password if not set and secret doesn't exist
if [[ -z "$KEYCLOAK_DB_PASSWORD" ]]; then
    echo "🔎 Checking if keycloak secret already exists..."
    if kubectl get secret "${TENANT}-keycloak-secrets" --namespace "${TENANT}" >/dev/null 2>&1; then
        echo "✅ Found existing keycloak secret; extracting DB password..."
        KEYCLOAK_DB_PASSWORD=$(kubectl get secret "${TENANT}-keycloak-secrets" --namespace "${TENANT}" -o jsonpath="{.data.db-password}" | base64 --decode)

        if [[ -z "$KEYCLOAK_DB_PASSWORD" ]]; then
            echo "❌ Secret exists but db-password is empty; exiting for safety"
            exit 1
        fi
    else
        echo "⚠️ No existing secret found; generating new Keycloak DB password..."
        KEYCLOAK_DB_PASSWORD=$(head /dev/urandom | tr -dc A-Za-z0-9 | head -c 24)
    fi
fi

# Generate Keycloak client secret if not already present
if [[ -z "$KEYCLOAK_CLIENT_SECRET" ]]; then
    echo "🔎 Checking if keycloak secret already exists..."
    if kubectl get secret "${TENANT}-keycloak-secrets" --namespace "${TENANT}" >/dev/null 2>&1; then
        echo "✅ Found existing keycloak secret; extracting client secret..."
        KEYCLOAK_CLIENT_SECRET=$(kubectl get secret "${TENANT}-keycloak-secrets" --namespace "${TENANT}" -o jsonpath="{.data.client-secret}" | base64 --decode)
    else
        echo "⚠️ No existing secret found; generating new Keycloak client secret..."
        KEYCLOAK_CLIENT_SECRET=$(head /dev/urandom | tr -dc A-Za-z0-9 | head -c 32)
    fi
fi

echo "Deploying Sentrius main chart to namespace ${TENANT}..."
helm upgrade --install sentrius ./sentrius-chart --namespace ${TENANT} \
    --set adminer.enabled=${DEPLOY_ADMINER} \
    --set tenant=${TENANT} \
    --set environment=${ENVIRONMENT} \
    --set subdomain="${SUBDOMAIN}" \
    --set metrics.enabled=true \
    --set agentproxySubdomain="${APROXY_SUBDOMAIN}" \
    --set rdpproxySubdomain="${RDPPROXY_SUBDOMAIN}" \
    --set keycloakSubdomain="${KEYCLOAK_SUBDOMAIN}" \
    --set keycloakHostname="${KEYCLOAK_HOSTNAME}" \
    --set keycloakDomain="${KEYCLOAK_DOMAIN}" \
    --set keycloakInternalDomain="${KEYCLOAK_INTERNAL_DOMAIN}" \
    --set sentriusDomain="${SENTRIUS_DOMAIN}" \
    --set secrets.db.password="${DB_PASSWORD}" \
    --set secrets.db.keystorePassword="${KEYSTORE_PASSWORD}" \
    --set agentproxyDomain="${APROXY_DOMAIN}" \
    --set rdpproxyDomain="${RDPPROXY_DOMAIN}" \
    --set certificates.enabled=${CERTIFICATES_ENABLED} \
    --set ingress.tlsEnabled=${INGRESS_TLS_ENABLED} \
    --set launcherFQDN=sentrius-agents-launcherservice.${TENANT}-agents.svc.cluster.local \
    --set integrationproxy.image.repository="${GCP_REGISTRY}/sentrius-integration-proxy" \
    --set integrationproxy.image.pullPolicy="IfNotPresent" \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set agentproxy.image.repository="${GCP_REGISTRY}/sentrius-agent-proxy" \
    --set agentproxy.image.pullPolicy="IfNotPresent" \
    --set agentproxy.image.tag=${AGENTPROXY_VERSION} \
    --set sentrius.image.repository="${GCP_REGISTRY}/sentrius" \
    --set sentrius.image.pullPolicy="IfNotPresent" \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set keycloak.db.password="${KEYCLOAK_DB_PASSWORD}" \
    --set secrets.db.username="postgres" \
    --set keycloak.adminPassword="${KEYCLOAK_ADMIN_PASSWORD}" \
    --set keycloak.clientSecret="${KEYCLOAK_CLIENT_SECRET}" \
    --set keycloak.realm.clients.sentriusApi.client_secret="${SENTRIUS_API_CLIENT_SECRET}" \
    --set keycloak.realm.clients.sentriusLauncher.client_secret="${SENTRIUS_LAUNCHER_CLIENT_SECRET}" \
    --set keycloak.realm.clients.javaAgents.client_secret="${JAVA_AGENTS_CLIENT_SECRET}" \
    --set keycloak.realm.clients.aiAgentAssessor.client_secret="${AI_AGENT_ASSESSOR_CLIENT_SECRET}" \
    --set keycloak.realm.clients.agentProxy.client_secret="${SENTRIUS_APROXY_CLIENT_SECRET}" \
    --set keycloak.image.repository="${GCP_REGISTRY}/sentrius-keycloak" \
    --set keycloak.image.pullPolicy="IfNotPresent" \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set ssh.image.repository="${GCP_REGISTRY}/sentrius-ssh" \
    --set ssh.image.pullPolicy="IfNotPresent" \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set sentriusaiagent.image.repository="${GCP_REGISTRY}/sentrius-ai-agent" \
    --set sentriusaiagent.image.pullPolicy="IfNotPresent" \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set launcherservice.image.repository="${GCP_REGISTRY}/sentrius-launcher-service" \
    --set launcherservice.image.pullPolicy="IfNotPresent" \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --set sshproxy.image.repository="${GCP_REGISTRY}/sentrius-ssh-proxy" \
    --set sshproxy.image.pullPolicy="IfNotPresent" \
    --set sshproxy.image.tag=${SSHPROXY_VERSION} \
    --set rdpproxy.image.repository="${GCP_REGISTRY}/sentrius-rdp-proxy" \
    --set rdpproxy.image.pullPolicy="IfNotPresent" \
    --set rdpproxy.image.tag=${RDPPROXY_VERSION} \
    --set rdpTest.enabled=${ENABLE_RDP_CONTAINER} \
    --set neo4j.env.NEO4J_server_config_strict__validation__enabled="\"false\"" \
    --set sentriusagent.image.repository="${GCP_REGISTRY}/sentrius-agent" \
    --set sentriusagent.image.pullPolicy="IfNotPresent" \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }

echo "Deploying Sentrius launcher chart to namespace ${TENANT}-agents..."
helm upgrade --install sentrius-agents ./sentrius-chart-launcher --namespace ${TENANT}-agents \
    --set tenant=${TENANT}-agents \
    --set baseRelease=sentrius \
    --set sentriusNamespace=${TENANT} \
    --set keycloakFQDN=sentrius-keycloak.${TENANT}.svc.cluster.local \
    --set sentriusFQDN=sentrius-sentrius.${TENANT}.svc.cluster.local \
    --set integrationproxyFQDN=sentrius-integrationproxy.${TENANT}.svc.cluster.local \
    --set agentproxyFQDN=sentrius-agentproxy.${TENANT}.svc.cluster.local \
    --set subdomain="${SUBDOMAIN}" \
    --set metrics.enabled=true \
    --set agentproxySubdomain="${APROXY_SUBDOMAIN}" \
    --set agentproxyDomain="${APROXY_DOMAIN}" \
    --set keycloakSubdomain="${KEYCLOAK_SUBDOMAIN}" \
    --set keycloakHostname="${KEYCLOAK_HOSTNAME}" \
    --set keycloakDomain="${KEYCLOAK_DOMAIN}" \
    --set keycloakInternalDomain="${KEYCLOAK_INTERNAL_DOMAIN}" \
    --set sentriusDomain="${SENTRIUS_DOMAIN}" \
    --set integrationproxy.image.repository="${GCP_REGISTRY}/sentrius-integration-proxy" \
    --set integrationproxy.image.pullPolicy="IfNotPresent" \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set secrets.db.password="${DB_PASSWORD}" \
    --set secrets.db.keystorePassword="${KEYSTORE_PASSWORD}" \
    --set launcherservice.oauth2.client_secret="${SENTRIUS_LAUNCHER_CLIENT_SECRET}" \
    --set sentrius.image.repository="${GCP_REGISTRY}/sentrius" \
    --set sentrius.image.pullPolicy="IfNotPresent" \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set keycloak.image.repository="${GCP_REGISTRY}/sentrius-keycloak" \
    --set keycloak.image.pullPolicy="IfNotPresent" \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set ssh.image.repository="${GCP_REGISTRY}/sentrius-ssh" \
    --set ssh.image.pullPolicy="IfNotPresent" \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set sentriusaiagent.image.repository="${GCP_REGISTRY}/sentrius-ai-agent" \
    --set sentriusaiagent.image.pullPolicy="IfNotPresent" \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set launcherservice.image.repository="${GCP_REGISTRY}/sentrius-launcher-service" \
    --set launcherservice.image.pullPolicy="IfNotPresent" \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --set neo4j.env.NEO4J_server_config_strict__validation__enabled="\"false\"" \
    --set sentriusagent.image.repository="${GCP_REGISTRY}/sentrius-agent" \
    --set sentriusagent.image.pullPolicy="IfNotPresent" \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius launcher with Helm"; exit 1; }

# Wait for LoadBalancer IPs to be ready
echo "Waiting for LoadBalancer IPs to be assigned..."
RETRIES=60
SLEEP_INTERVAL=10

for ((i=1; i<=RETRIES; i++)); do
    # Retrieve LoadBalancer IP
    INGRESS_IP=$(kubectl get ingress managed-cert-ingress-${TENANT} -n ${TENANT} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

    if [[ -n "$INGRESS_IP" ]]; then
        echo "INGRESS_IP: $INGRESS_IP"
        break
    fi

    echo "Attempt $i: Waiting for IPs to be assigned..."
    sleep $SLEEP_INTERVAL
done

if [[ -z "$INGRESS_IP" ]]; then
    echo "Failed to retrieve LoadBalancer IPs after $((RETRIES * SLEEP_INTERVAL)) seconds."
    exit 1
fi

# Check if subdomain exists
if gcloud dns record-sets list --zone=${ZONE} --name=${TENANT}.sentrius.cloud. | grep -q ${TENANT}.sentrius.cloud.; then
    echo "Subdomain ${TENANT}.sentrius.cloud already exists. Skipping creation."
else
    echo "Creating subdomain ${TENANT}.sentrius.cloud..."
    gcloud dns record-sets transaction start --zone=${ZONE}

    gcloud dns record-sets transaction add --zone=${ZONE} \
          --name=${TENANT}.sentrius.cloud. \
          --type=A \
          --ttl=300 \
          $INGRESS_IP

    gcloud dns record-sets transaction add --zone=${ZONE} \
      --name=keycloak.${TENANT}.sentrius.cloud. \
      --type=A \
      --ttl=300 \
      $INGRESS_IP

    gcloud dns record-sets transaction add --zone=${ZONE} \
      --name=agentproxy.${TENANT}.sentrius.cloud. \
      --type=A \
      --ttl=300 \
      $INGRESS_IP

    gcloud dns record-sets transaction add --zone=${ZONE} \
      --name=rdpproxy.${TENANT}.sentrius.cloud. \
      --type=A \
      --ttl=300 \
      $INGRESS_IP

    gcloud dns record-sets transaction execute --zone=${ZONE}
fi

echo "✅ Deployment complete!"
echo "Sentrius Domain: ${SENTRIUS_DOMAIN}"
echo "Keycloak Domain: ${KEYCLOAK_DOMAIN}"
echo "Agent Proxy Domain: ${APROXY_DOMAIN}"
echo "RDP Proxy Domain: ${RDPPROXY_DOMAIN}"