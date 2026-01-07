#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../base/base.sh
source ${SCRIPT_DIR}/../../.azure.env

# For AKS deployments, use versioned tags from .azure.env
# Default to 'latest' if .azure.env is not sourced or variables are not set
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
MONITORING_AGENT_VERSION="${MONITORING_AGENT_VERSION:-latest}"
SSH_AGENT_VERSION="${SSH_AGENT_VERSION:-latest}"
AZURE_REGISTRY="${AZURE_REGISTRY:-sentriusacr.azurecr.io}"

# Azure DNS Configuration
RESOURCE_GROUP="${AZURE_RESOURCE_GROUP:-sentrius-rg}" #change this for your deployment
DNS_ZONE="trustpolicy.ai"  # Your DNS zone name

TENANT=""
ENV_TARGET="azure"
CERTIFICATES_ENABLED="true"
INGRESS_TLS_ENABLED="true"
ENVIRONMENT="azure"
DEPLOY_ADMINER=${DEPLOY_ADMINER:-false}
ENABLE_RDP_CONTAINER=${ENABLE_RDP_CONTAINER:-true}

# Azure Container Registry
AZURE_REGISTRY="${AZURE_REGISTRY:-sentriusacr.azurecr.io}"

# Generate secrets using the shared script
(source ${SCRIPT_DIR}/../base/generate-secrets.sh)

GENERATED_ENV_PATH="${SCRIPT_DIR}/../../.generated.env"
if [[ -f "$GENERATED_ENV_PATH" ]]; then
    source "$GENERATED_ENV_PATH"
fi

DOMAIN_NAME="trustpolicy.ai"  # Default domain for Azure

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --tenant)
            TENANT="$2"
            shift 2
            ;;
        --domain)
            DOMAIN_NAME="$2"
            shift 2
            ;;
        --resource_group)
            RESOURCE_GROUP="$2"
            shift 2
            ;;
        --no-tls)
            CERTIFICATES_ENABLED="false"
            INGRESS_TLS_ENABLED="false"
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 --tenant TENANT_NAME [--domain DOMAIN] [--no-tls]"
            echo "  --tenant: Specify tenant name (required)"
            echo "  --domain: Specify domain name (default: trustpolicy.ai)"
            echo "  --no-tls: Disable TLS/SSL (not recommended for production)"
            exit 1
            ;;
    esac
done

if [[ -z "$TENANT" ]]; then
    echo "Must provide tenant name with --tenant" 1>&2
    echo "Usage: $0 --tenant TENANT_NAME [--domain DOMAIN] [--no-tls]"
    exit 1
fi

# Configure domain settings for AKS
SUBDOMAIN="${TENANT}.${DOMAIN_NAME}"
APROXY_SUBDOMAIN="agentproxy.${TENANT}.${DOMAIN_NAME}"
KEYCLOAK_SUBDOMAIN="keycloak.${TENANT}.${DOMAIN_NAME}"
RDPPROXY_SUBDOMAIN="rdpproxy.${TENANT}.${DOMAIN_NAME}"
KEYCLOAK_HOSTNAME="${KEYCLOAK_SUBDOMAIN}"
KEYCLOAK_DOMAIN="https://${KEYCLOAK_SUBDOMAIN}"
KEYCLOAK_INTERNAL_DOMAIN="${KEYCLOAK_DOMAIN}"
SENTRIUS_DOMAIN="https://${SUBDOMAIN}"
APROXY_DOMAIN="https://${APROXY_SUBDOMAIN}"
RDPPROXY_DOMAIN="https://${RDPPROXY_SUBDOMAIN}"
STORAGE_CLASS_NAME="managed-premium"

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

# ==========================================
# 🔍 Check/Install NGINX Ingress Controller
# ==========================================
echo "🔍 Checking for NGINX Ingress Controller..."

if ! kubectl get namespace ingress-nginx >/dev/null 2>&1; then
    echo "📦 NGINX Ingress Controller not found. Installing..."

    helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx >/dev/null 2>&1 || true
    helm repo update ingress-nginx

    helm install ingress-nginx ingress-nginx/ingress-nginx \
        --create-namespace \
        --namespace ingress-nginx \
        --set controller.service.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-health-probe-request-path"=/healthz \
        --wait \
        --timeout=10m || { echo "❌ Failed to install NGINX Ingress Controller"; exit 1; }

    echo "✅ NGINX Ingress Controller installed"
else
    echo "✅ NGINX Ingress Controller already installed"
fi

# Wait for ingress controller to get external IP
echo "⏳ Waiting for NGINX Ingress Controller to get external IP..."
INGRESS_IP=""
for i in {1..60}; do
    INGRESS_IP=$(kubectl get svc ingress-nginx-controller -n ingress-nginx -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [[ -n "$INGRESS_IP" ]]; then
        echo "✅ NGINX Ingress Controller has external IP: $INGRESS_IP"
        break
    fi
    if [ $((i % 10)) -eq 0 ]; then
        echo "  Still waiting for external IP... ($i/60)"
    fi
    sleep 5
done

if [[ -z "$INGRESS_IP" ]]; then
    echo "⚠️ WARNING: NGINX Ingress Controller did not get an external IP within 5 minutes"
    echo "  Continuing anyway, but ingresses may not work..."
fi

# Wait for admission webhooks to be ready (prevents validation failures during deployment)
echo "🔍 Checking for admission webhooks..."

# Wait for admission webhooks to be ready (prevents validation failures during deployment)
echo "🔍 Checking for admission webhooks..."

# Check for ingress controller webhook
if kubectl get validatingwebhookconfigurations 2>/dev/null | grep -q "ingress"; then
    echo "⏳ Waiting for ingress admission webhook to be ready..."
    for i in {1..30}; do
        if kubectl get validatingwebhookconfigurations 2>/dev/null | grep -q "ingress.*admission"; then
            echo "✅ Ingress admission webhook is configured"
            sleep 2
            break
        fi
        echo "Waiting for ingress webhook configuration... ($i/30)"
        sleep 2
    done
fi

# Check for cert-manager webhook (only if TLS is enabled)
if [[ "$CERTIFICATES_ENABLED" == "true" ]]; then
    if kubectl get validatingwebhookconfigurations cert-manager-webhook >/dev/null 2>&1; then
        echo "⏳ Waiting for cert-manager webhook to be fully operational..."
        if kubectl get pods -n cert-manager -l app.kubernetes.io/name=webhook >/dev/null 2>&1; then
            kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=webhook \
                -n cert-manager \
                -l app.kubernetes.io/name=webhook \
                --timeout=60s 2>/dev/null || \
                echo "⚠️ cert-manager webhook may not be fully ready"
        fi
        echo "✅ cert-manager webhook check complete"
        kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.3/cert-manager.yaml

        kubectl wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout=300s

cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: marc@sentrius.io  # CHANGE THIS
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF

        sleep 2
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

# ==========================================
# 🔍 Render Helm Output for Validation
# ==========================================
RENDER_PATH="${SCRIPT_DIR}/rendered-${TENANT}.yaml"

echo "📄 Rendering Helm chart (dry run) for validation..."
helm template sentrius ./sentrius-chart \
    --namespace ${TENANT} \
    --set adminer.enabled=${DEPLOY_ADMINER} \
    --set tenant=${TENANT} \
    --set environment=${ENVIRONMENT} \
    --set ingress.class="nginx" \
    --set subdomain="${SUBDOMAIN}" \
    --set metrics.enabled=true \
    --set healthCheck.backendConfig.enabled=false \
    --set config.storageClassName="${STORAGE_CLASS_NAME}" \
    --set agentproxySubdomain="${APROXY_SUBDOMAIN}" \
    --set rdpproxySubdomain="${RDPPROXY_SUBDOMAIN}" \
    --set keycloakSubdomain="${KEYCLOAK_SUBDOMAIN}" \
    --set keycloakHostname="${KEYCLOAK_HOSTNAME}" \
    --set keycloakDomain="${KEYCLOAK_DOMAIN}" \
    --set keycloakInternalDomain="${KEYCLOAK_DOMAIN}" \
    --set sentriusDomain="${SENTRIUS_DOMAIN}" \
    --set agentproxyDomain="${APROXY_DOMAIN}" \
    --set rdpproxyDomain="${RDPPROXY_DOMAIN}" \
    --set certificates.enabled=${CERTIFICATES_ENABLED} \
    --set ingress.tlsEnabled=${INGRESS_TLS_ENABLED} \
    > "${RENDER_PATH}"

if [[ $? -ne 0 ]]; then
    echo "❌ Helm rendering failed — check your templates!"
    exit 1
fi

echo "✅ Rendered output saved to ${RENDER_PATH}"

# Validate YAML
echo "🔍 Validating Kubernetes YAML with kubeval (if installed)..."
if command -v kubeval >/dev/null 2>&1; then
    kubeval --strict "${RENDER_PATH}"
else
    echo "⚠️ kubeval not installed — skipping schema validation."
fi

echo "======================================"
echo "🚀 Deploying Sentrius (Two-Stage Ingress)"
echo "======================================"

echo "📦 Deploying Sentrius main chart to namespace ${TENANT}..."
helm upgrade --install sentrius ./sentrius-chart --namespace ${TENANT} \
    --set adminer.enabled=${DEPLOY_ADMINER} \
    --set tenant=${TENANT} \
    --set environment="${ENVIRONMENT}" \
    --set ingress.class="nginx" \
    --set subdomain="${SUBDOMAIN}" \
    --set metrics.enabled=true \
    --set healthCheck.backendConfig.enabled=false \
    --set config.storageClassName="${STORAGE_CLASS_NAME}" \
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
    --set integrationproxy.image.repository="${AZURE_REGISTRY}/sentrius-integration-proxy" \
    --set integrationproxy.image.pullPolicy="IfNotPresent" \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set agentproxy.image.repository="${AZURE_REGISTRY}/sentrius-agent-proxy" \
    --set agentproxy.image.pullPolicy="IfNotPresent" \
    --set agentproxy.image.tag=${AGENTPROXY_VERSION} \
    --set sentrius.image.repository="${AZURE_REGISTRY}/sentrius" \
    --set sentrius.image.pullPolicy="IfNotPresent" \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set keycloak.db.password="${KEYCLOAK_DB_PASSWORD}" \
    --set secrets.db.username="postgres" \
    --set keycloak.adminPassword="${KEYCLOAK_ADMIN_PASSWORD}" \
    --set keycloak.clientSecret="${KEYCLOAK_CLIENT_SECRET}" \
    --set keycloak.realm.clients.sentriusApi.client_secret="${SENTRIUS_API_CLIENT_SECRET}" \
    --set keycloak.realm.clients.sentriusLauncher.client_secret="${SENTRIUS_LAUNCHER_CLIENT_SECRET}" \
    --set keycloak.realm.clients.javaAgents.client_secret="${JAVA_AGENTS_CLIENT_SECRET}" \
    --set keycloak.realm.clients.aiAgentAssessor.client_secret="${MONITORING_AGENT_CLIENT_SECRET}" \
    --set keycloak.realm.clients.sshagent.client_secret="${SSH_AGENT_CLIENT_SECRET}" \
    --set keycloak.realm.clients.agentProxy.client_secret="${SENTRIUS_APROXY_CLIENT_SECRET}" \
    --set keycloak.realm.clients.promptAdvisor.client_secret="${PROMPT_ADVISOR_CLIENT_SECRET}" \
    --set keycloak.image.repository="${AZURE_REGISTRY}/sentrius-keycloak" \
    --set keycloak.image.pullPolicy="IfNotPresent" \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set ssh.image.repository="${AZURE_REGISTRY}/sentrius-ssh" \
    --set ssh.image.pullPolicy="IfNotPresent" \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set sentriusaiagent.image.repository="${AZURE_REGISTRY}/sentrius-ai-agent" \
    --set sentriusaiagent.image.pullPolicy="IfNotPresent" \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set launcherservice.image.repository="${AZURE_REGISTRY}/sentrius-launcher-service" \
    --set launcherservice.image.pullPolicy="IfNotPresent" \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --set sshproxy.image.repository="${AZURE_REGISTRY}/sentrius-ssh-proxy" \
    --set sshproxy.image.pullPolicy="IfNotPresent" \
    --set sshproxy.image.tag=${SSHPROXY_VERSION} \
    --set monitoringagent.image.tag=${MONITORING_AGENT_VERSION} \
    --set monitoringagent.image.repository="${AZURE_REGISTRY}/sentrius-monitoring-agent" \
    --set monitoringagent.image.pullPolicy="IfNotPresent" \
    --set sshagent.image.tag=${SSH_AGENT_VERSION} \
    --set sshagent.image.repository="${AZURE_REGISTRY}/sentrius-ssh-agent" \
    --set rdpproxy.image.repository="${AZURE_REGISTRY}/sentrius-rdp-proxy" \
    --set rdpproxy.image.pullPolicy="IfNotPresent" \
    --set rdpproxy.image.tag=${RDPPROXY_VERSION} \
    --set rdpTest.enabled=${ENABLE_RDP_CONTAINER} \
    --set neo4j.env.NEO4J_server_config_strict__validation__enabled="\"false\"" \
    --set sentriusagent.image.repository="${AZURE_REGISTRY}/sentrius-agent" \
    --set sentriusagent.image.pullPolicy="IfNotPresent" \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }

echo ""
echo "======================================"
echo "⏳ STAGE 1: Waiting for Keycloak Ingress"
echo "======================================"

# Wait for Keycloak ingress to get an IP
KEYCLOAK_INGRESS_TIMEOUT=600
ELAPSED=0
KEYCLOAK_INGRESS_IP=""

echo "Waiting for Keycloak ingress IP (timeout: ${KEYCLOAK_INGRESS_TIMEOUT}s)..."
while [ $ELAPSED -lt $KEYCLOAK_INGRESS_TIMEOUT ]; do
    KEYCLOAK_INGRESS_IP=$(kubectl get ingress "keycloak-ingress-${TENANT}" -n ${TENANT} -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

    if [[ -n "$KEYCLOAK_INGRESS_IP" ]]; then
        echo "✅ Keycloak ingress has IP: $KEYCLOAK_INGRESS_IP"
        break
    fi

    if [ $((ELAPSED % 30)) -eq 0 ]; then
        echo "  Still waiting for Keycloak ingress IP... ($ELAPSED seconds elapsed)"
    fi
    sleep 10
    ELAPSED=$((ELAPSED + 10))
done

if [[ -z "$KEYCLOAK_INGRESS_IP" ]]; then
    echo "❌ ERROR: Keycloak ingress did not get an IP within ${KEYCLOAK_INGRESS_TIMEOUT} seconds"
    echo ""
    echo "Checking ingress status:"
    kubectl describe ingress "keycloak-ingress-${TENANT}" -n ${TENANT}
    exit 1
fi

# Create/Update DNS for Keycloak immediately
echo ""
echo "🌐 Configuring DNS for Keycloak..."
if az network dns record-set a show --resource-group ${RESOURCE_GROUP} --zone-name ${DNS_ZONE} --name keycloak.${TENANT} 2>/dev/null | grep -q "keycloak.${TENANT}"; then
    echo "  Updating existing DNS record for ${KEYCLOAK_SUBDOMAIN}..."
    az network dns record-set a remove-record --resource-group ${RESOURCE_GROUP} --zone-name ${DNS_ZONE} --record-set-name keycloak.${TENANT} --ipv4-address $(az network dns record-set a show --resource-group ${RESOURCE_GROUP} --zone-name ${DNS_ZONE} --name keycloak.${TENANT} --query 'aRecords[0].ipv4Address' -o tsv) 2>/dev/null || true
fi

az network dns record-set a add-record \
    --resource-group ${RESOURCE_GROUP} \
    --zone-name ${DNS_ZONE} \
    --record-set-name keycloak.${TENANT} \
    --ipv4-address $KEYCLOAK_INGRESS_IP || {
    echo "⚠️ Failed to create DNS record, it may already exist"
}

# Wait for Keycloak pod to be ready
echo ""
echo "⏳ Waiting for Keycloak pod to be ready..."
kubectl wait --for=condition=ready pod \
    -l "app.kubernetes.io/name=keycloak" \
    -n ${TENANT} \
    --timeout=10m || {
    echo "⚠️ Keycloak pod not ready yet, but continuing..."
}

# Wait for Keycloak to respond
echo ""
echo "⏳ Waiting for Keycloak to be healthy..."
echo "  Checking: https://${KEYCLOAK_SUBDOMAIN}/"
KEYCLOAK_HEALTH_TIMEOUT=300
ELAPSED=0

while [ $ELAPSED -lt $KEYCLOAK_HEALTH_TIMEOUT ]; do
    # Try HTTPS (with DNS), then HTTP with IP
    if curl -sf -k --connect-timeout 5 "https://${KEYCLOAK_SUBDOMAIN}/" >/dev/null 2>&1; then
        echo "✅ Keycloak is healthy via HTTPS"
        break
    elif curl -sf --connect-timeout 5 "http://${KEYCLOAK_INGRESS_IP}/" >/dev/null 2>&1; then
        echo "✅ Keycloak is responding (certificate may still be provisioning)"
        break
    fi

    if [ $((ELAPSED % 30)) -eq 0 ]; then
        echo "  Waiting for Keycloak to respond... ($ELAPSED seconds elapsed)"
    fi
    sleep 10
    ELAPSED=$((ELAPSED + 10))
done

if [ $ELAPSED -ge $KEYCLOAK_HEALTH_TIMEOUT ]; then
    echo "⚠️ WARNING: Keycloak did not respond within ${KEYCLOAK_HEALTH_TIMEOUT} seconds"
    echo "  Continuing anyway - apps will retry connection..."
fi

echo ""
echo "======================================"
echo "⏳ STAGE 2: Waiting for Apps Ingress"
echo "======================================"

# Wait for apps ingress to get an IP
APPS_INGRESS_TIMEOUT=600
ELAPSED=0
APPS_INGRESS_IP=""

echo "Waiting for apps ingress IP (timeout: ${APPS_INGRESS_TIMEOUT}s)..."
while [ $ELAPSED -lt $APPS_INGRESS_TIMEOUT ]; do
    APPS_INGRESS_IP=$(kubectl get ingress "apps-ingress-${TENANT}" -n ${TENANT} -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

    if [[ -n "$APPS_INGRESS_IP" ]]; then
        echo "✅ Apps ingress has IP: $APPS_INGRESS_IP"
        break
    fi

    if [ $((ELAPSED % 30)) -eq 0 ]; then
        echo "  Still waiting for apps ingress IP... ($ELAPSED seconds elapsed)"
    fi
    sleep 10
    ELAPSED=$((ELAPSED + 10))
done

if [[ -z "$APPS_INGRESS_IP" ]]; then
    echo "⚠️ WARNING: Apps ingress did not get an IP within ${APPS_INGRESS_TIMEOUT} seconds"
    echo "  Application pods may still be starting up..."
else
    # Configure DNS for apps
    echo ""
    echo "🌐 Configuring DNS for application services..."

    # Check and create/update DNS records
    for SUBDOMAIN_NAME in "${SUBDOMAIN}" "${APROXY_SUBDOMAIN}" "${RDPPROXY_SUBDOMAIN}"; do
        RECORD_NAME=$(echo ${SUBDOMAIN_NAME} | sed "s/\.${DNS_ZONE}//")
        if az network dns record-set a show --resource-group ${RESOURCE_GROUP} --zone-name ${DNS_ZONE} --name ${RECORD_NAME} 2>/dev/null | grep -q "${RECORD_NAME}"; then
            echo "  Updating ${SUBDOMAIN_NAME}..."
            az network dns record-set a remove-record --resource-group ${RESOURCE_GROUP} --zone-name ${DNS_ZONE} --record-set-name ${RECORD_NAME} --ipv4-address $(az network dns record-set a show --resource-group ${RESOURCE_GROUP} --zone-name ${DNS_ZONE} --name ${RECORD_NAME} --query 'aRecords[0].ipv4Address' -o tsv) 2>/dev/null || true
        fi

        az network dns record-set a add-record \
            --resource-group ${RESOURCE_GROUP} \
            --zone-name ${DNS_ZONE} \
            --record-set-name ${RECORD_NAME} \
            --ipv4-address $APPS_INGRESS_IP || {
            echo "⚠️ Failed to create DNS record for ${SUBDOMAIN_NAME}"
        }
    done
fi

# Deploy launcher service
echo ""
echo "======================================"
echo "📦 Deploying Launcher Service"
echo "======================================"

echo "Deploying Sentrius launcher chart to namespace ${TENANT}-agents..."
helm upgrade --install sentrius-agents ./sentrius-chart-launcher --namespace ${TENANT}-agents \
    --set tenant=${TENANT}-agents \
    --set base-tenant=${TENANT} \
    --set baseRelease=sentrius \
    --set sentriusNamespace=${TENANT} \
    --set ingress.class="nginx" \
    --set healthCheck.backendConfig.enabled=false \
    --set config.usePVC=true \
    --set config.storageClassName="${STORAGE_CLASS_NAME}" \
    --set config.storageSize="1Gi" \
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
    --set integrationproxy.image.repository="${AZURE_REGISTRY}/sentrius-integration-proxy" \
    --set integrationproxy.image.pullPolicy="IfNotPresent" \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set secrets.db.password="${DB_PASSWORD}" \
    --set secrets.db.keystorePassword="${KEYSTORE_PASSWORD}" \
    --set launcherservice.oauth2.client_secret="${SENTRIUS_LAUNCHER_CLIENT_SECRET}" \
    --set sentrius.image.repository="${AZURE_REGISTRY}/sentrius" \
    --set sentrius.image.pullPolicy="IfNotPresent" \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set keycloak.image.repository="${AZURE_REGISTRY}/sentrius-keycloak" \
    --set keycloak.image.pullPolicy="IfNotPresent" \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set ssh.image.repository="${AZURE_REGISTRY}/sentrius-ssh" \
    --set ssh.image.pullPolicy="IfNotPresent" \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set sentriusaiagent.image.repository="${AZURE_REGISTRY}/" \
    --set sentriusaiagent.image.pullPolicy="IfNotPresent" \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set launcherservice.image.repository="${AZURE_REGISTRY}/sentrius-launcher-service" \
    --set launcherservice.image.pullPolicy="IfNotPresent" \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --set neo4j.env.NEO4J_server_config_strict__validation__enabled="\"false\"" \
    --set sentriusagent.image.repository="${AZURE_REGISTRY}/sentrius-agent" \
    --set sentriusagent.image.pullPolicy="IfNotPresent" \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius launcher with Helm"; exit 1; }

# Wait for application pods
echo ""
echo "⏳ Waiting for application pods to be ready..."
kubectl wait --for=condition=ready pod \
    -l "app.kubernetes.io/instance=sentrius" \
    -n ${TENANT} \
    --timeout=10m 2>&1 | grep -v "error: no matching resources found" || true

echo ""
echo "======================================"
echo "✅ Deployment Complete!"
echo "======================================"
echo ""
echo "Keycloak Ingress IP: ${KEYCLOAK_INGRESS_IP}"
echo "Apps Ingress IP:     ${APPS_INGRESS_IP:-<pending>}"
echo ""
echo "Services:"
echo "  Keycloak:    ${KEYCLOAK_DOMAIN}"
echo "  Sentrius:    ${SENTRIUS_DOMAIN}"
echo "  Agent Proxy: ${APROXY_DOMAIN}"
echo "  RDP Proxy:   ${RDPPROXY_DOMAIN}"
echo ""
echo "Check status with:"
echo "  kubectl get ingress -n ${TENANT}"
echo "  kubectl get pods -n ${TENANT}"
