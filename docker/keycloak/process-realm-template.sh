#!/bin/bash

# Process realm template with environment variable substitution
# This script replaces environment variable placeholders in the realm template
# with actual values before Keycloak imports the realm

# Set paths - different for container vs local testing
if [ -f "/opt/keycloak/data/import/sentrius-realm.json.template" ]; then
    # Container environment
    REALM_TEMPLATE="/opt/keycloak/data/import/sentrius-realm.json.template"
    REALM_OUTPUT="/opt/keycloak/data/import/sentrius-realm.json"
elif [ -f "./realms/sentrius-realm.json.template" ]; then
    # Local testing environment
    REALM_TEMPLATE="./realms/sentrius-realm.json.template"
    REALM_OUTPUT="${REALM_OUTPUT:-./sentrius-realm.json}"
else
    echo "Error: Realm template not found"
    echo "  Looked for: /opt/keycloak/data/import/sentrius-realm.json.template"
    echo "  Looked for: ./realms/sentrius-realm.json.template"
    exit 1
fi

echo "Processing Keycloak realm template..."
echo "  Template: $REALM_TEMPLATE"
echo "  Output: $REALM_OUTPUT"

# Set default values for secrets if not provided
if command -v openssl >/dev/null 2>&1; then
    # Use openssl if available
    export SENTRIUS_API_CLIENT_SECRET="${SENTRIUS_API_CLIENT_SECRET:-default-api-secret-$(openssl rand -hex 16)}"
    export SENTRIUS_LAUNCHER_CLIENT_SECRET="${SENTRIUS_LAUNCHER_CLIENT_SECRET:-default-launcher-secret-$(openssl rand -hex 16)}"
    export JAVA_AGENTS_CLIENT_SECRET="${JAVA_AGENTS_CLIENT_SECRET:-default-agents-secret-$(openssl rand -hex 16)}"
    export AI_AGENT_ASSESSOR_CLIENT_SECRET="${AI_AGENT_ASSESSOR_CLIENT_SECRET:-default-assessor-secret-$(openssl rand -hex 16)}"
else
    # Fallback to simple random generation using date and process ID
    RAND_SUFFIX=$(date +%s%N | cut -b1-13)$$
    export SENTRIUS_API_CLIENT_SECRET="${SENTRIUS_API_CLIENT_SECRET:-default-api-secret-${RAND_SUFFIX}}"
    export SENTRIUS_LAUNCHER_CLIENT_SECRET="${SENTRIUS_LAUNCHER_CLIENT_SECRET:-default-launcher-secret-${RAND_SUFFIX}a}"
    export JAVA_AGENTS_CLIENT_SECRET="${JAVA_AGENTS_CLIENT_SECRET:-default-agents-secret-${RAND_SUFFIX}b}"
    export AI_AGENT_ASSESSOR_CLIENT_SECRET="${AI_AGENT_ASSESSOR_CLIENT_SECRET:-default-assessor-secret-${RAND_SUFFIX}c}"
fi

# Set default values for other placeholders
# set in helm chart
#export ROOT_URL="${ROOT_URL:-http://localhost:8080}"
# set in helm chart
#export REDIRECT_URIS="${REDIRECT_URIS:-http://localhost:8080}"
export GOOGLE_CLIENT_ID="${GOOGLE_CLIENT_ID:google-oauth-sentrius}"
export GOOGLE_CLIENT_SECRET="${GOOGLE_CLIENT_SECRET:-}"

echo "Substituting environment variables in realm template..."
echo "  SENTRIUS_API_CLIENT_SECRET: ${SENTRIUS_API_CLIENT_SECRET:0:8}..."
echo "  SENTRIUS_LAUNCHER_CLIENT_SECRET: ${SENTRIUS_LAUNCHER_CLIENT_SECRET:0:8}..."
echo "  JAVA_AGENTS_CLIENT_SECRET: ${JAVA_AGENTS_CLIENT_SECRET:0:8}..."
echo "  AI_AGENT_ASSESSOR_CLIENT_SECRET: ${AI_AGENT_ASSESSOR_CLIENT_SECRET:0:8}..."

# Use sed to replace environment variables (since envsubst may not be available)
# Replace ${VAR} with actual values
sed -e "s|\${SENTRIUS_API_CLIENT_SECRET}|${SENTRIUS_API_CLIENT_SECRET}|g" \
    -e "s|\${SENTRIUS_LAUNCHER_CLIENT_SECRET}|${SENTRIUS_LAUNCHER_CLIENT_SECRET}|g" \
    -e "s|\${JAVA_AGENTS_CLIENT_SECRET}|${JAVA_AGENTS_CLIENT_SECRET}|g" \
    -e "s|\${AI_AGENT_ASSESSOR_CLIENT_SECRET}|${AI_AGENT_ASSESSOR_CLIENT_SECRET}|g" \
    -e "s|\${GOOGLE_CLIENT_ID}|${GOOGLE_CLIENT_ID}|g" \
    -e "s|\${GOOGLE_CLIENT_SECRET}|${GOOGLE_CLIENT_SECRET}|g" \
    "$REALM_TEMPLATE" > "$REALM_OUTPUT"

  # these two are set helm chart
  #    -e "s|\${ROOT_URL}|${ROOT_URL}|g" \
  #    -e "s|\${REDIRECT_URIS}|${REDIRECT_URIS}|g" \

if [ $? -eq 0 ]; then
    echo "Realm template processed successfully: $REALM_OUTPUT"
else
    echo "Error: Failed to process realm template"
    exit 1
fi

# Validate the JSON is valid
if command -v jq >/dev/null 2>&1; then
    if ! jq empty < "$REALM_OUTPUT" >/dev/null 2>&1; then
        echo "Error: Generated realm JSON is invalid"
        exit 1
    fi
    echo "Generated realm JSON is valid"
else
    echo "Note: jq not available, skipping JSON validation"
fi
