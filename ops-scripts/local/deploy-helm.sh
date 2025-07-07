#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../base/base.sh
source ${SCRIPT_DIR}/../../.local.env

CERT_DIR="${SCRIPT_DIR}/../../docker/dev-certs"
CERT_FILE="${CERT_DIR}/sentrius-ca.crt"
KEY_FILE="${CERT_DIR}/sentrius-ca.key"
TENANT=dev
ENABLE_TLS=false
INSTALL_CERT_MANAGER=false
ENV_TARGET="local"  # default mode
CERT_DIR="${SCRIPT_DIR}/../../docker/dev-certs"

# --- Load and back up environment file ---
ENV_FILE="${SCRIPT_DIR}/../../.$ENV_TARGET.env"
source "$ENV_FILE"
cp "$ENV_FILE" "$ENV_FILE.bak"

(source ${SCRIPT_DIR}/../base/generate-secrets.sh)

GENERATED_ENV_PATH="${SCRIPT_DIR}/../../.generated.env"
if [[ -f "$GENERATED_ENV_PATH" ]]; then
    source "$GENERATED_ENV_PATH"
fi

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


if [[ "$ENABLE_TLS" == "true"  ]]; then
  if [[ ! -f "$CERT_FILE" || ! -f "$KEY_FILE" ]]; then
      echo "🔧 Generating dev TLS certificate..."
      openssl req -x509 -newkey rsa:2048 -nodes \
          -keyout "$KEY_FILE" \
          -out "$CERT_FILE" \
          -days 365 \
          -subj "/CN=sentrius-dev-ca" \
          -addext "basicConstraints=critical,CA:TRUE" \
          -addext "keyUsage=critical,keyCertSign,cRLSign"


      echo "Creating dev CA secret in cluster"
      kubectl -n cert-manager delete secret sentrius-dev-ca 2>/dev/null || true
      kubectl -n cert-manager create secret tls sentrius-dev-ca \
        --cert="$CERT_DIR/sentrius-ca.crt" \
        --key="$CERT_DIR/sentrius-ca.key"

      echo "Rebuilding docker images with dev certs included"

      ${SCRIPT_DIR}/../base/build-images.sh --all --include-dev-certs

  else
      echo "✅ Dev cert already exists at $CERT_FILE"


      echo "Creating dev CA secret in cluster"
      kubectl -n cert-manager delete secret sentrius-dev-ca 2>/dev/null || true
      kubectl -n cert-manager create secret tls sentrius-dev-ca \
        --cert="$CERT_DIR/sentrius-ca.crt" \
        --key="$CERT_DIR/sentrius-ca.key"
  fi
fi


# Function to check if cert-manager is installed and ready
check_cert_manager() {
    echo "Checking if cert-manager is installed..."
    
    # Check if cert-manager deployments are present
if ! kubectl get deployment cert-manager -n cert-manager >/dev/null 2>&1 || \
   ! kubectl get deployment cert-manager-webhook -n cert-manager >/dev/null 2>&1 || \
   ! kubectl get deployment cert-manager-cainjector -n cert-manager >/dev/null 2>&1; then
     echo "cert-manager deployments not found in cert-manager namespace."
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
        echo "Waiting for cert-manager to be ready..."
        kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=cert-manager -n cert-manager --timeout=300s
        kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=webhook -n cert-manager --timeout=300s
    else
        echo "ERROR: cert-manager is not fully installed in your cluster."
        echo "You can install it manually or rerun this script with --install-cert-manager --tls"
        exit 1
    fi
fi

}

# Configure TLS settings
if [[ "$ENABLE_TLS" == "true" ]]; then
    echo "Deploying with TLS enabled..."
    check_cert_manager
    SUBDOMAIN="sentrius-${TENANT}.local"
    APROXY_SUBDOMAIN="agentproxy-${TENANT}.local"
    KEYCLOAK_SUBDOMAIN="keycloak-${TENANT}.local"
    KEYCLOAK_HOSTNAME=${KEYCLOAK_SUBDOMAIN}
    KEYCLOAK_DOMAIN="https://${KEYCLOAK_SUBDOMAIN}"
    KEYCLOAK_INTERNAL_DOMAIN="https://${KEYCLOAK_SUBDOMAIN}"
    SENTRIUS_DOMAIN="https://${SUBDOMAIN}"
    APROXY_DOMAIN="https://${APROXY_SUBDOMAIN}"
    CERTIFICATES_ENABLED="true"
    INGRESS_TLS_ENABLED="true"
    ENVIRONMENT="local"
else
    echo "Deploying with HTTP (no TLS)..."
    SUBDOMAIN="sentrius-sentrius"
    APROXY_SUBDOMAIN="sentrius-agentproxy"
    KEYCLOAK_SUBDOMAIN="sentrius-keycloak"
    KEYCLOAK_HOSTNAME="sentrius-keycloak:8081"
    KEYCLOAK_DOMAIN="http://sentrius-keycloak:8081"
    KEYCLOAK_INTERNAL_DOMAIN="http://sentrius-keycloak:8081"
    APROXY_DOMAIN="http://sentrius-agentproxy:8080"
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

helm upgrade --install sentrius ./sentrius-chart --namespace ${TENANT} \
    --set adminer.enabled=true \
    --set tenant=${TENANT} \
    --set environment=${ENVIRONMENT} \
    --set subdomain="${SUBDOMAIN}" \
    --set agentproxySubdomain="${APROXY_SUBDOMAIN}" \
    --set keycloakSubdomain="${KEYCLOAK_SUBDOMAIN}" \
    --set keycloakHostname="${KEYCLOAK_HOSTNAME}" \
    --set keycloakDomain="${KEYCLOAK_DOMAIN}" \
    --set keycloakInternalDomain="${KEYCLOAK_INTERNAL_DOMAIN}" \
    --set sentriusDomain="${SENTRIUS_DOMAIN}" \
    --set secrets.db.password="${DB_PASSWORD}" \
    --set secrets.db.keystorePassword="${KEYSTORE_PASSWORD}" \
    --set agentproxyDomain="${APROXY_DOMAIN}" \
    --set certificates.enabled=${CERTIFICATES_ENABLED} \
    --set ingress.tlsEnabled=${INGRESS_TLS_ENABLED} \
    --set launcherFQDN=sentrius-agents-launcherservice.${TENANT}-agents.svc.cluster.local \
    --set integrationproxy.image.repository="sentrius-integration-proxy" \
    --set agentproxy.image.pullPolicy="Never" \
    --set agentproxy.image.tag=${AGENTPROXY_VERSION} \
    --set integrationproxy.image.pullPolicy="Never" \
    --set sentrius.image.repository="sentrius" \
    --set keycloak.db.password="${KEYCLOAK_DB_PASSWORD}" \
    --set secrets.db.username="postgres" \
    --set keycloak.db.password="${KEYCLOAK_DB_PASSWORD}" \
    --set keycloak.adminPassword="${KEYCLOAK_ADMIN_PASSWORD}" \
    --set keycloak.clientSecret="${KEYCLOAK_CLIENT_SECRET}" \
    --set keycloak.realm.clients.sentriusApi.client_secret="${SENTRIUS_API_CLIENT_SECRET}" \
    --set keycloak.realm.clients.sentriusLauncher.client_secret="${SENTRIUS_LAUNCHER_CLIENT_SECRET}" \
    --set keycloak.realm.clients.javaAgents.client_secret="${JAVA_AGENTS_CLIENT_SECRET}" \
    --set keycloak.realm.clients.aiAgentAssessor.client_secret="${AI_AGENT_ASSESSOR_CLIENT_SECRET}" \
    --set keycloak.realm.clients.agentProxy.client_secret="${SENTRIUS_APROXY_CLIENT_SECRET}" \
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
    --set integrationproxyFQDN=sentrius-integrationproxy.${TENANT}.svc.cluster.local \
    --set agentproxyFQDN=sentrius-llmproxy.${TENANT}.svc.cluster.local \
    --set subdomain="${SUBDOMAIN}" \
    --set agentproxySubdomain="${APROXY_SUBDOMAIN}" \
    --set agentproxyDomain="${APROXY_DOMAIN}" \
    --set keycloakSubdomain="${KEYCLOAK_SUBDOMAIN}" \
    --set keycloakHostname="${KEYCLOAK_HOSTNAME}" \
    --set keycloakDomain="${KEYCLOAK_DOMAIN}" \
    --set keycloakInternalDomain="${KEYCLOAK_INTERNAL_DOMAIN}" \
    --set sentriusDomain="${SENTRIUS_DOMAIN}" \
    --set integrationproxy.image.repository="sentrius-integration-proxy" \
    --set integrationproxy.image.pullPolicy="Never" \
    --set secrets.db.password="${DB_PASSWORD}" \
    --set secrets.db.keystorePassword="${KEYSTORE_PASSWORD}" \
    --set launcherservice.oauth2.client_secret="${SENTRIUS_LAUNCHER_CLIENT_SECRET}" \
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