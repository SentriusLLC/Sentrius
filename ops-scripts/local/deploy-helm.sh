#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.local.env

TENANT=dev
ENABLE_TLS=false
INSTALL_CERT_MANAGER=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --tls)
            ENABLE_TLS=true
            shift
            ;;
        --install-cert-manager)
            INSTALL_CERT_MANAGER=true
            shift
            ;;
        --tenant)
            TENANT="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--tls] [--install-cert-manager] [--tenant TENANT_NAME]"
            echo "  --tls: Enable TLS/SSL for secure transport"
            echo "  --install-cert-manager: Automatically install cert-manager if not present"
            echo "  --tenant: Specify tenant name (default: dev)"
            exit 1
            ;;
    esac
done

if [[ -z "$TENANT" ]]; then
    echo "Must provide tenant name" 1>&2
    exit 1
fi

# Function to check if cert-manager is installed and ready
check_cert_manager() {
    echo "Checking if cert-manager is installed..."
    
    # Check if cert-manager deployments are present
if ! kubectl get deployment cert-manager -n cert-manager >/dev/null 2>&1 || \
   ! kubectl get deployment cert-manager-webhook -n cert-manager >/dev/null 2>&1 || \
   ! kubectl get deployment cert-manager-cainjector -n cert-manager >/dev/null 2>&1; then
    if [[ "$INSTALL_CERT_MANAGER" == "true" ]]; then
        echo "cert-manager components not found. Installing via Helm..."
        helm repo add jetstack https://charts.jetstack.io
        helm repo update
        helm upgrade --install cert-manager jetstack/cert-manager \
          --namespace cert-manager \
          --create-namespace \
          --set installCRDs=true
        if [[ $? -ne 0 ]]; then
            echo "ERROR: Failed to install cert-manager with Helm"
            exit 1
        fi
    else
        echo "ERROR: cert-manager is not fully installed in your cluster."
        echo "You can install it manually or rerun this script with --install-cert-manager --tls"
        exit 1
    fi
fi

}

# Function to wait for cert-manager CRDs and webhook to be ready
wait_for_cert_manager_crds() {
    local max_attempts=30
    local attempt=1
    
    while [[ $attempt -le $max_attempts ]]; do
        # Check if Certificate CRD is available and webhook is ready
        if kubectl get crd certificates.cert-manager.io >/dev/null 2>&1 && \
           kubectl get crd clusterissuers.cert-manager.io >/dev/null 2>&1; then
            
            # Test if we can actually create cert-manager resources by doing a dry-run
            echo "Testing cert-manager webhook readiness..."
            kubectl create --dry-run=server -o yaml - <<EOF >/dev/null 2>&1
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: test-issuer
spec:
  selfSigned: {}
EOF
            if [[ $? -eq 0 ]]; then
                echo "cert-manager CRDs and webhook are ready ✓"
                return 0
            fi
        fi
        
        echo "Waiting for cert-manager CRDs and webhook to be ready (attempt $attempt/$max_attempts)..."
        sleep 10
        ((attempt++))
    done
    
    echo "ERROR: cert-manager CRDs or webhook are not ready after $((max_attempts * 10)) seconds"
    echo "This may indicate an issue with cert-manager installation."
    echo ""
    echo "Try running these commands to check cert-manager status:"
    echo "  kubectl get pods -n cert-manager"
    echo "  kubectl logs -n cert-manager -l app.kubernetes.io/name=cert-manager"
    echo "  kubectl get crd | grep cert-manager"
    exit 1
}

# Configure TLS settings
if [[ "$ENABLE_TLS" == "true" ]]; then
    echo "Deploying with TLS enabled..."
    check_cert_manager
    SUBDOMAIN="sentrius-${TENANT}.local"
    KEYCLOAK_SUBDOMAIN="keycloak-${TENANT}.local"
    KEYCLOAK_HOSTNAME="${KEYCLOAK_SUBDOMAIN}"
    KEYCLOAK_DOMAIN="https://${KEYCLOAK_SUBDOMAIN}"
    SENTRIUS_DOMAIN="https://${SUBDOMAIN}"
    CERTIFICATES_ENABLED="true"
    INGRESS_TLS_ENABLED="true"
    ENVIRONMENT="local"
else
    echo "Deploying with HTTP (no TLS)..."
    SUBDOMAIN="sentrius-sentrius"
    KEYCLOAK_SUBDOMAIN="sentrius-keycloak"
    KEYCLOAK_HOSTNAME="sentrius-keycloak:8081"
    KEYCLOAK_DOMAIN="http://sentrius-keycloak:8081"
    SENTRIUS_DOMAIN="http://sentrius-sentrius:8080"
    CERTIFICATES_ENABLED="false"
    INGRESS_TLS_ENABLED="false"
    ENVIRONMENT="local"
fi

# Check if namespace exists
kubectl get namespace ${TENANT} >/dev/null 2>&1
if [[ $? -ne 0 ]]; then
    echo "Namespace ${TENANT} does not exist. Creating..."
    kubectl create namespace ${TENANT} || { echo "Failed to create namespace ${TENANT}"; exit 1; }
    kubectl create namespace ${TENANT}-agents || { echo "Failed to create namespace ${TENANT}"; exit 1; }
fi

kubectl get namespace ${TENANT}-agents >/dev/null 2>&1
if [[ $? -ne 0 ]]; then
    echo "Namespace ${TENANT}-agents does not exist. Creating..."
    kubectl create namespace ${TENANT}-agents || { echo "Failed to create namespace ${TENANT}"; exit 1; }
fi
#    --set sentrius-ssh.image.pullPolicy="Never" \
#    --set sentrius-keycloak.image.pullPolicy="Never" \
#    --set sentrius-bad-ssh.image.pullPolicy="Never" \

# Load any previously generated password from .generated.env
GENERATED_ENV_PATH="${SCRIPT_DIR}/../../.generated.env"
if [[ -f "$GENERATED_ENV_PATH" ]]; then
    source "$GENERATED_ENV_PATH"
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

        # Persist it to .generated.env so it doesn't change between runs
        echo "KEYCLOAK_DB_PASSWORD=${KEYCLOAK_DB_PASSWORD}" > "$GENERATED_ENV_PATH"
    fi
fi


helm upgrade --install sentrius ./sentrius-chart --namespace ${TENANT} \
    --set tenant=${TENANT} \
    --set environment=${ENVIRONMENT} \
    --set subdomain="${SUBDOMAIN}" \
    --set keycloakSubdomain="${KEYCLOAK_SUBDOMAIN}" \
    --set keycloakHostname="${KEYCLOAK_HOSTNAME}" \
    --set keycloakDomain="${KEYCLOAK_DOMAIN}" \
    --set sentriusDomain="${SENTRIUS_DOMAIN}" \
    --set certificates.enabled=${CERTIFICATES_ENABLED} \
    --set ingress.tlsEnabled=${INGRESS_TLS_ENABLED} \
    --set launcherFQDN=sentrius-agents-launcherservice.${TENANT}-agents.svc.cluster.local \
    --set integrationproxy.image.repository="sentrius-integration-proxy" \
    --set integrationproxy.image.pullPolicy="Never" \
    --set sentrius.image.repository="sentrius" \
    --set keycloak.db.password="${KEYCLOAK_DB_PASSWORD}" \
    --set sentrius.image.pullPolicy="Never" \
    --set keycloak.image.pullPolicy="Never" \
    --set ssh.image.pullPolicy="Never" \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set launcherservice.image.pullPolicy="Never" \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --set neo4j.env.NEO4J_server_config_strict__validation__enabled="\"false\"" \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }


helm upgrade --install sentrius-agents ./sentrius-chart-launcher --namespace ${TENANT}-agents \
    --set tenant=${TENANT}-agents \
    --set baseRelease=sentrius \
    --set sentriusNamespace=${TENANT} \
    --set keycloakFQDN=sentrius-keycloak.${TENANT}.svc.cluster.local \
    --set sentriusFQDN=sentrius-sentrius.${TENANT}.svc.cluster.local \
    --set integrationproxyFQDN=sentrius-llmproxy.${TENANT}.svc.cluster.local \
    --set subdomain="${SUBDOMAIN}" \
    --set keycloakSubdomain="${KEYCLOAK_SUBDOMAIN}" \
    --set keycloakHostname="${KEYCLOAK_HOSTNAME}" \
    --set keycloakDomain="${KEYCLOAK_DOMAIN}" \
    --set sentriusDomain="${SENTRIUS_DOMAIN}" \
    --set integrationproxy.image.repository="sentrius-llmproxy" \
    --set integrationproxy.image.pullPolicy="Never" \
    --set sentrius.image.repository="sentrius" \
    --set sentrius.image.pullPolicy="Never" \
    --set keycloak.image.pullPolicy="Never" \
    --set ssh.image.pullPolicy="Never" \
    --set integrationproxy.image.tag=${LLMPROXY_VERSION} \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set sentriusaiagent.image.tag=${SENTRIUS_AI_AGENT_VERSION} \
    --set launcherservice.image.pullPolicy="Never" \
    --set launcherservice.image.tag=${LAUNCHER_VERSION} \
    --set neo4j.env.NEO4J_server_config_strict__validation__enabled="\"false\"" \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }