#!/bin/bash

# --- Set default mode ---
ENV_TARGET="local"  # default mode
NO_CACHE=false

# --- Parse the environment target (local | gcp) ---
if [[ "$1" == "local" || "$1" == "gcp" ]]; then
    ENV_TARGET="$1"
    shift
fi

# --- Load and back up environment file ---
ENV_FILE=".$ENV_TARGET.env"
source "$ENV_FILE"
cp "$ENV_FILE" "$ENV_FILE.bak"

# --- Minikube Docker context ---
if [[ "$ENV_TARGET" == "local" ]]; then
    eval $(minikube -p minikube docker-env)
fi

# --- Helpers ---
update_env_var() {
    local key=$1
    local value=$2
    if grep -q "^$key=" "$ENV_FILE"; then
        sed -i "s/^$key=.*/$key=$value/" "$ENV_FILE"
    else
        echo "$key=$value" >> "$ENV_FILE"
    fi
}

increment_patch_version() {
    version=$1
    major=$(echo "$version" | cut -d. -f1)
    minor=$(echo "$version" | cut -d. -f2)
    patch=$(echo "$version" | cut -d. -f3)
    echo "$major.$minor.$((patch + 1))"
}

build_image() {
    local name=$1
    local version=$2
    local context_dir=$3

    echo "Building $name:$version..."

    if $NO_CACHE; then
        docker build --no-cache -t "$name:$version" "$context_dir"
    else
        docker build -t "$name:$version" "$context_dir"
    fi

    if [ $? -ne 0 ]; then
        echo "❌ Failed to build $name"
        exit 1
    fi

    if [[ "$ENV_TARGET" == "gcp" ]]; then
        REGISTRY="us-central1-docker.pkg.dev/sentrius-project/sentrius-repo"
        docker tag "$name:$version" "$REGISTRY/$name:$version"
        docker push "$REGISTRY/$name:$version"
        echo "✅ Pushed $REGISTRY/$name:$version"
    else
        docker tag "$name:$version" "$name:latest"
        echo "✅ Built locally: $name:$version"
    fi
}

# --- Flags ---
update_sentrius=false
update_sentrius_ssh=false
update_sentrius_keycloak=false
update_sentrius_agent=false
update_sentrius_ai_agent=false
update_integrationproxy=false
update_launcher=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --sentrius) update_sentrius=true ;;
        --sentrius-ssh) update_sentrius_ssh=true ;;
        --sentrius-keycloak) update_sentrius_keycloak=true ;;
        --sentrius-agent) update_sentrius_agent=true ;;
        --sentrius-ai-agent) update_sentrius_ai_agent=true ;;
        --sentrius-launcher-service) update_launcher=true ;;
        --sentrius-integration-proxy) update_integrationproxy=true ;;
        --all) update_sentrius=true; update_sentrius_ssh=true; update_sentrius_keycloak=true; update_sentrius_agent=true; update_sentrius_ai_agent=true; update_integrationproxy=true; update_launcher=true ;;
        --no-cache) NO_CACHE=true ;;
        *) echo "Unknown flag: $1"; exit 1 ;;
    esac
    shift
done

# --- Auth for GCP ---
if [[ "$ENV_TARGET" == "gcp" ]]; then
    echo "Authenticating with Google Cloud..."
    gcloud auth configure-docker us-central1-docker.pkg.dev || exit 1
fi

# --- Build Steps ---
if $update_sentrius; then
    SENTRIUS_VERSION=$(increment_patch_version $SENTRIUS_VERSION)
    build_image "sentrius" "$SENTRIUS_VERSION" "."
    update_env_var "SENTRIUS_VERSION" "$SENTRIUS_VERSION"
fi

if $update_sentrius_ssh; then
    SENTRIUS_SSH_VERSION=$(increment_patch_version $SENTRIUS_SSH_VERSION)
    build_image "sentrius-ssh" "$SENTRIUS_SSH_VERSION" "./docker/fake-ssh"
    update_env_var "SENTRIUS_SSH_VERSION" "$SENTRIUS_SSH_VERSION"
fi

if $update_sentrius_keycloak; then
    SENTRIUS_KEYCLOAK_VERSION=$(increment_patch_version $SENTRIUS_KEYCLOAK_VERSION)
    build_image "sentrius-keycloak" "$SENTRIUS_KEYCLOAK_VERSION" "./docker/keycloak"
    update_env_var "SENTRIUS_KEYCLOAK_VERSION" "$SENTRIUS_KEYCLOAK_VERSION"
fi

if $update_sentrius_agent; then
    cp analytics/target/analytics-*.jar docker/sentrius-agent/agent.jar
    SENTRIUS_AGENT_VERSION=$(increment_patch_version $SENTRIUS_AGENT_VERSION)
    build_image "sentrius-agent" "$SENTRIUS_AGENT_VERSION" "./docker/sentrius-agent"
    rm docker/sentrius-agent/agent.jar
    update_env_var "SENTRIUS_AGENT_VERSION" "$SENTRIUS_AGENT_VERSION"
fi

if $update_sentrius_ai_agent; then
    cp ai-agent/target/ai-agent-*.jar docker/sentrius-ai-agent/agent.jar
    SENTRIUS_AI_AGENT_VERSION=$(increment_patch_version $SENTRIUS_AI_AGENT_VERSION)
    build_image "sentrius-ai-agent" "$SENTRIUS_AI_AGENT_VERSION" "./docker/sentrius-ai-agent"
    rm docker/sentrius-ai-agent/agent.jar
    update_env_var "SENTRIUS_AI_AGENT_VERSION" "$SENTRIUS_AI_AGENT_VERSION"

    cp ai-agent/target/ai-agent-*.jar docker/sentrius-launchable-agent/agent.jar
    build_image "sentrius-launchable-agent" "$SENTRIUS_AI_AGENT_VERSION" "./docker/sentrius-launchable-agent"
    rm docker/sentrius-launchable-agent/agent.jar
fi

if $update_integrationproxy; then
    cp integration-proxy/target/sentrius-integration-proxy-*.jar docker/integrationproxy/llmproxy.jar
    LLMPROXY_VERSION=$(increment_patch_version $LLMPROXY_VERSION)
    build_image "sentrius-integration-proxy" "$LLMPROXY_VERSION" "./docker/integrationproxy"
    rm docker/integrationproxy/llmproxy.jar
    update_env_var "LLMPROXY_VERSION" "$LLMPROXY_VERSION"
fi

if $update_launcher; then
    cp agent-launcher/target/agent-launcher-*.jar docker/sentrius-launcher-service/launcher.jar
    LAUNCHER_VERSION=$(increment_patch_version $LAUNCHER_VERSION)
    build_image "sentrius-launcher-service" "$LAUNCHER_VERSION" "./docker/sentrius-launcher-service"
    rm docker/sentrius-launcher-service/launcher.jar
    update_env_var "LAUNCHER_VERSION" "$LAUNCHER_VERSION"
fi