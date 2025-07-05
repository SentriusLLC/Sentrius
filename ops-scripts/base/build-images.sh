#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh

# --- Set default mode ---
ENV_TARGET="local"  # default mode
NO_CACHE=false
INCLUDE_DEV_CERTS=false

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

prepare_docker_context() {
    local context_dir=$1

    if $INCLUDE_DEV_CERTS; then
        echo "Including dev certificates in Docker context..."
        mkdir -p "$context_dir/dev-certs"
        cp "$context_dir/../dev-certs/sentrius-ca.crt" "$context_dir/dev-certs/"
    else
        echo "Excluding dev certificates from Docker context..."
        rm -rf "$context_dir/dev-certs"
        mkdir -p "$context_dir/dev-certs"
        cp "$context_dir/../dev-certs/empty-sentrius-ca.crt" "$context_dir/dev-certs/sentrius-ca.crt"
    fi
}

cleanup_docker_context() {
    local context_dir=$1
    #rm -rf "$context_dir/dev-certs"
}

build_image() {
    local name=$1
    local version=$2
    local context_dir=$3

    echo "Building $name:$version..."
    prepare_docker_context "$context_dir"

    BUILD_ARGS=()
    if $INCLUDE_DEV_CERTS; then
        BUILD_ARGS+=(--build-arg INCLUDE_DEV_CERTS=true)
    fi

    if $NO_CACHE; then
        docker build --no-cache "${BUILD_ARGS[@]}" -t "$name:$version" "$context_dir"
    else
        docker build "${BUILD_ARGS[@]}" -t "$name:$version" "$context_dir"
    fi

    if [ $? -ne 0 ]; then
        echo "❌ Failed to build $name"
        cleanup_docker_context "$context_dir"
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

    cleanup_docker_context "$context_dir"
}

# --- Flags ---
update_sentrius=false
update_sentrius_ssh=false
update_sentrius_keycloak=false
update_sentrius_agent=false
update_sentrius_ai_agent=false
update_integrationproxy=false
update_launcher=false
update_agent_proxy=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --sentrius) update_sentrius=true ;;
        --sentrius-ssh) update_sentrius_ssh=true ;;
        --sentrius-keycloak) update_sentrius_keycloak=true ;;
        --sentrius-agent) update_sentrius_agent=true ;;
        --sentrius-ai-agent) update_sentrius_ai_agent=true ;;
        --sentrius-launcher-service) update_launcher=true ;;
        --sentrius-integration-proxy) update_integrationproxy=true ;;
        --sentrius-agent-proxy) update_agent_proxy=true ;;
        --all) update_sentrius=true; update_sentrius_ssh=true; update_sentrius_keycloak=true; update_sentrius_agent=true; update_sentrius_ai_agent=true; update_integrationproxy=true; update_launcher=true; update_agent_proxy=true; ;;
        --no-cache) NO_CACHE=true ;;
        --include-dev-certs) INCLUDE_DEV_CERTS=true ;;
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
    cp api/target/sentrius-api-*.jar docker/sentrius/sentrius.jar
    SENTRIUS_VERSION=$(increment_patch_version $SENTRIUS_VERSION)
    build_image "sentrius" "$SENTRIUS_VERSION" "${SCRIPT_DIR}/../../docker/sentrius/"
    rm docker/sentrius/sentrius.jar
    update_env_var "SENTRIUS_VERSION" "$SENTRIUS_VERSION"
fi

if $update_sentrius_ssh; then
    SENTRIUS_SSH_VERSION=$(increment_patch_version $SENTRIUS_SSH_VERSION)
    build_image "sentrius-ssh" "$SENTRIUS_SSH_VERSION" "${SCRIPT_DIR}/../../docker/fake-ssh"
    update_env_var "SENTRIUS_SSH_VERSION" "$SENTRIUS_SSH_VERSION"
fi

if $update_sentrius_keycloak; then
    SENTRIUS_KEYCLOAK_VERSION=$(increment_patch_version $SENTRIUS_KEYCLOAK_VERSION)
    build_image "sentrius-keycloak" "$SENTRIUS_KEYCLOAK_VERSION" "${SCRIPT_DIR}/../../docker/keycloak"
    update_env_var "SENTRIUS_KEYCLOAK_VERSION" "$SENTRIUS_KEYCLOAK_VERSION"
fi

if $update_sentrius_agent; then
    cp analytics/target/analytics-*.jar docker/sentrius-agent/agent.jar
    SENTRIUS_AGENT_VERSION=$(increment_patch_version $SENTRIUS_AGENT_VERSION)
    build_image "sentrius-agent" "$SENTRIUS_AGENT_VERSION" "${SCRIPT_DIR}/../../docker/sentrius-agent"
    rm docker/sentrius-agent/agent.jar
    update_env_var "SENTRIUS_AGENT_VERSION" "$SENTRIUS_AGENT_VERSION"
fi

if $update_sentrius_ai_agent; then
    cp ai-agent/target/ai-agent-*.jar docker/sentrius-ai-agent/agent.jar
    SENTRIUS_AI_AGENT_VERSION=$(increment_patch_version $SENTRIUS_AI_AGENT_VERSION)
    build_image "sentrius-ai-agent" "$SENTRIUS_AI_AGENT_VERSION" "${SCRIPT_DIR}/../../docker/sentrius-ai-agent"
    rm docker/sentrius-ai-agent/agent.jar
    update_env_var "SENTRIUS_AI_AGENT_VERSION" "$SENTRIUS_AI_AGENT_VERSION"

    cp ai-agent/target/ai-agent-*.jar docker/sentrius-launchable-agent/agent.jar
    build_image "sentrius-launchable-agent" "$SENTRIUS_AI_AGENT_VERSION" "${SCRIPT_DIR}/../../docker/sentrius-launchable-agent"
    rm docker/sentrius-launchable-agent/agent.jar
fi

if $update_integrationproxy; then
    cp integration-proxy/target/sentrius-integration-proxy-*.jar docker/integrationproxy/llmproxy.jar
    LLMPROXY_VERSION=$(increment_patch_version $LLMPROXY_VERSION)
    build_image "sentrius-integration-proxy" "$LLMPROXY_VERSION" "${SCRIPT_DIR}/../../docker/integrationproxy"
    rm docker/integrationproxy/llmproxy.jar
    update_env_var "LLMPROXY_VERSION" "$LLMPROXY_VERSION"
fi

if $update_launcher; then
    cp agent-launcher/target/agent-launcher-*.jar docker/sentrius-launcher-service/launcher.jar
    LAUNCHER_VERSION=$(increment_patch_version $LAUNCHER_VERSION)
    build_image "sentrius-launcher-service" "$LAUNCHER_VERSION" "${SCRIPT_DIR}/../../docker/sentrius-launcher-service"
    rm docker/sentrius-launcher-service/launcher.jar
    update_env_var "LAUNCHER_VERSION" "$LAUNCHER_VERSION"
fi

if $update_agent_proxy; then
    cp agent-proxy/target/sentrius-agent-proxy-*.jar docker/agent-proxy/agentproxy.jar
    AGENTPROXY_VERSION=$(increment_patch_version $AGENTPROXY_VERSION)
    build_image "sentrius-agent-proxy" "$AGENTPROXY_VERSION" "${SCRIPT_DIR}/../../docker/agent-proxy"
    rm docker/agent-proxy/agentproxy.jar
    update_env_var "AGENTPROXY_VERSION" "$AGENTPROXY_VERSION"
fi