#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)


TENANT="${TENANT:-dev}"
GENERATED_ENV_PATH=${SCRIPT_DIR}/../../".generated.env"

echo "🔐 Generating secrets for tenant: $TENANT"

# Step 1: Source existing secrets if the file exists
if [[ -f "$GENERATED_ENV_PATH" ]]; then
  echo "📂 Loading existing secrets from $GENERATED_ENV_PATH"
  # shellcheck disable=SC1090
  source "$GENERATED_ENV_PATH"
fi

# Step 2: Function to get or generate secrets
get_or_generate_secret() {
    local secret_name="$1"
    local var_name="$2"
    local key="$3"
    local length="$4"

    # If already exported in env, skip regeneration
    if [[ -n "${!var_name:-}" ]]; then
        echo "🔒 $var_name is already set, skipping regeneration" >&2
        echo "${!var_name}"
        return
    fi

    echo "🔎 Checking if $secret_name/$key exists..." >&2
    if kubectl get secret "${secret_name}" --namespace "${TENANT}" >/dev/null 2>&1; then
        local result
        result=$(kubectl get secret "${secret_name}" -n "${TENANT}" -o jsonpath="{.data.${key}}" | base64 --decode)
        if [[ -z "$result" ]]; then
            echo "❌ Secret $secret_name exists but key $key is empty" >&2
            exit 1
        fi
        echo "✅ Using existing $key from $secret_name" >&2
        export "$var_name=$result"
        echo "$result"
    else
        echo "⚠️ No existing secret $secret_name found; generating new $key" >&2
        local result
        result=$(head /dev/urandom | tr -dc A-Za-z0-9 | head -c "$length")
        export "$var_name=$result"
        echo "$result"
    fi
}


# Base secrets from Kubernetes secrets
KEYCLOAK_SECRET="${TENANT}-keycloak-secrets"
APP_DB_SECRET="${TENANT}-db-secret"

KEYCLOAK_DB_PASSWORD=$(get_or_generate_secret "$KEYCLOAK_SECRET" "KEYCLOAK_DB_PASSWORD" "db-password" 24)
KEYCLOAK_CLIENT_SECRET=$(get_or_generate_secret "$KEYCLOAK_SECRET" "KEYCLOAK_CLIENT_SECRET" "client-secret" 32)
KEYCLOAK_ADMIN_PASSWORD=$(get_or_generate_secret "$KEYCLOAK_SECRET" "KEYCLOAK_ADMIN_PASSWORD" "admin-password" 24)

DB_PASSWORD=$(get_or_generate_secret "$APP_DB_SECRET" "DB_PASSWORD" "db-password" 32)
KEYSTORE_PASSWORD=$(get_or_generate_secret "$APP_DB_SECRET" "KEYSTORE_PASSWORD" "keystore-password" 32)

# App-specific OAuth2 client secrets (not in existing k8s secret yet)
SENTRIUS_API_CLIENT_SECRET=$(get_or_generate_secret "$KEYCLOAK_SECRET" "SENTRIUS_API_CLIENT_SECRET" "SENTRIUS_API_CLIENT_SECRET" 32)
SENTRIUS_APROXY_CLIENT_SECRET=$(get_or_generate_secret "$KEYCLOAK_SECRET" "SENTRIUS_APROXY_CLIENT_SECRET" "SENTRIUS_APROXY_CLIENT_SECRET" 32)
SENTRIUS_LAUNCHER_CLIENT_SECRET=$(get_or_generate_secret "$KEYCLOAK_SECRET" "SENTRIUS_LAUNCHER_CLIENT_SECRET" "SENTRIUS_LAUNCHER_CLIENT_SECRET" 32)
JAVA_AGENTS_CLIENT_SECRET=$(get_or_generate_secret "$KEYCLOAK_SECRET" "JAVA_AGENTS_CLIENT_SECRET" "JAVA_AGENTS_CLIENT_SECRET" 32)
MONITORING_AGENT_CLIENT_SECRET=$(get_or_generate_secret "$KEYCLOAK_SECRET" "MONITORING_AGENT_CLIENT_SECRET" "MONITORING_AGENT_CLIENT_SECRET" 32)
SSH_AGENT_CLIENT_SECRET=$(get_or_generate_secret "$KEYCLOAK_SECRET" "SSH_AGENT_CLIENT_SECRET" "SSH_AGENT_CLIENT_SECRET" 32)
SENTRIUS_RDPPROXY_CLIENT_SECRET=$(get_or_generate_secret "$KEYCLOAK_SECRET" "SENTRIUS_RDPPROXY_CLIENT_SECRET" "SENTRIUS_RDPPROXY_CLIENT_SECRET" 32)
PROMPT_ADVISOR_CLIENT_SECRET=$(get_or_generate_secret "$KEYCLOAK_SECRET" "PROMPT_ADVISOR_CLIENT_SECRET" "PROMPT_ADVISOR_CLIENT_SECRET" 32)

# Output to .generated.env
cat <<EOF > "$GENERATED_ENV_PATH"
KEYCLOAK_DB_PASSWORD=${KEYCLOAK_DB_PASSWORD}
KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET}
KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD}
DB_PASSWORD=${DB_PASSWORD}
KEYSTORE_PASSWORD=${KEYSTORE_PASSWORD}
SENTRIUS_API_CLIENT_SECRET=${SENTRIUS_API_CLIENT_SECRET}
SENTRIUS_APROXY_CLIENT_SECRET=${SENTRIUS_APROXY_CLIENT_SECRET}
SENTRIUS_LAUNCHER_CLIENT_SECRET=${SENTRIUS_LAUNCHER_CLIENT_SECRET}
JAVA_AGENTS_CLIENT_SECRET=${JAVA_AGENTS_CLIENT_SECRET}
MONITORING_AGENT_CLIENT_SECRET=${MONITORING_AGENT_CLIENT_SECRET}
SSH_AGENT_CLIENT_SECRET=${SSH_AGENT_CLIENT_SECRET}
SENTRIUS_RDPPROXY_CLIENT_SECRET=${SENTRIUS_RDPPROXY_CLIENT_SECRET}
PROMPT_ADVISOR_CLIENT_SECRET=${PROMPT_ADVISOR_CLIENT_SECRET}
EOF

echo "📦 Secrets written to $GENERATED_ENV_PATH"
