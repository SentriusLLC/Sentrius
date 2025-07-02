#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)


source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.local.env

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
    --set subdomain="sentrius-sentrius" \
    --set keycloakSubdomain="sentrius-keycloak" \
    --set keycloakHostname="sentrius-keycloak:8081" \
    --set keycloakDomain="http://sentrius-keycloak:8081" \
    --set sentriusDomain="http://sentrius-sentrius:8080" \
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
    --set integrationproxyFQDN=sentrius-integrationproxy.${TENANT}.svc.cluster.local \
    --set subdomain="sentrius-sentrius" \
    --set keycloakSubdomain="sentrius-keycloak" \
    --set keycloakHostname="sentrius-keycloak:8081" \
    --set keycloakDomain="http://sentrius-keycloak:8081" \
    --set sentriusDomain="http://sentrius-sentrius:8080" \
    --set integrationproxy.image.repository="sentrius-integration-proxy" \
    --set integrationproxy.image.pullPolicy="IfNotPresent" \
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