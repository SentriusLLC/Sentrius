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

# --- Load environment file only for GCP (versions needed for registry) ---
if [[ "$ENV_TARGET" == "gcp" ]]; then
    ENV_FILE=".$ENV_TARGET.env"
    source "$ENV_FILE"
    cp "$ENV_FILE" "$ENV_FILE.bak"
fi

# --- Minikube Docker context ---
if [[ "$ENV_TARGET" == "local" ]]; then
    NODE_COUNT=$(minikube -p minikube node list 2>/dev/null | grep -c 'minikube')
    if [[ "$NODE_COUNT" -eq 1 ]]; then
        echo "🟢 Single-node Minikube detected — using docker-env"
        eval $(minikube -p minikube docker-env)
        USE_MINIKUBE_LOAD=false
    else
        echo "🟠 Multi-node Minikube detected — will use 'minikube image load' after builds"
        USE_MINIKUBE_LOAD=true
    fi
fi


GENERATED_ENV_PATH="${SCRIPT_DIR}/../../.generated.env"
if [[ -f "$GENERATED_ENV_PATH" ]]; then
    source "$GENERATED_ENV_PATH"
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
    local dockerfile_flag=""
    local skip_dev_certs=false
    
    # Check for optional -f flag for custom Dockerfile
    if [[ "$4" == "-f" ]]; then
        dockerfile_flag="-f $5"
    fi
    
    # Check for --skip-dev-certs flag
    if [[ "$4" == "--skip-dev-certs" ]] || [[ "$6" == "--skip-dev-certs" ]]; then
        skip_dev_certs=true
    fi

    # For local builds, always use 'latest' tag
    if [[ "$ENV_TARGET" == "local" ]]; then
        version="latest"
    fi

    echo "Building $name:$version..."
    
    # Only prepare docker context if not skipping dev-certs
    if ! $skip_dev_certs; then
        prepare_docker_context "$context_dir"
    fi

    BUILD_ARGS=()
    if $INCLUDE_DEV_CERTS && ! $skip_dev_certs; then
        BUILD_ARGS+=(--build-arg INCLUDE_DEV_CERTS=true)
    fi

    if $NO_CACHE; then
        docker build --no-cache "${BUILD_ARGS[@]}" $dockerfile_flag -t "$name:$version" "$context_dir"
    else
        docker build "${BUILD_ARGS[@]}" $dockerfile_flag -t "$name:$version" "$context_dir"
    fi

    # Sync image into Minikube if running multi-node
    if [[ "$ENV_TARGET" == "local" && "$USE_MINIKUBE_LOAD" == true ]]; then
        echo "📦 Loading $name:$version into Minikube nodes..."
        minikube image load "$name:$version"
    fi

    if [ $? -ne 0 ]; then
        echo "❌ Failed to build $name"
        if ! $skip_dev_certs; then
            cleanup_docker_context "$context_dir"
        fi
        exit 1
    fi

    if [[ "$ENV_TARGET" == "gcp" ]]; then
        REGISTRY="us-central1-docker.pkg.dev/sentrius-project/sentrius-repo"
        docker tag "$name:$version" "$REGISTRY/$name:$version"
        docker push "$REGISTRY/$name:$version"
        echo "✅ Pushed $REGISTRY/$name:$version"
    else
        echo "✅ Built locally: $name:$version"
    fi

    if ! $skip_dev_certs; then
        cleanup_docker_context "$context_dir"
    fi
}

build_keycloak_image() {
    local name=$1
    local version=$2
    local context_dir=$3

    # For local builds, always use 'latest' tag
    if [[ "$ENV_TARGET" == "local" ]]; then
        version="latest"
    fi

    echo "Building $name:$version..."
    prepare_docker_context "$context_dir"

    BUILD_ARGS=()
    if $INCLUDE_DEV_CERTS; then
        BUILD_ARGS+=(--build-arg INCLUDE_DEV_CERTS=true)
    fi

    if $NO_CACHE; then
        docker build --no-cache "${BUILD_ARGS[@]}" -t "$name:$version" \
          --build-arg SENTRIUS_API_CLIENT_SECRET="$SENTRIUS_API_CLIENT_SECRET" \
          --build-arg SENTRIUS_APROXY_CLIENT_SECRET="$SENTRIUS_APROXY_CLIENT_SECRET" \
          --build-arg SENTRIUS_LAUNCHER_CLIENT_SECRET="$SENTRIUS_LAUNCHER_CLIENT_SECRET" \
          --build-arg JAVA_AGENTS_CLIENT_SECRET="$JAVA_AGENTS_CLIENT_SECRET" \
          --build-arg MONITORING_AGENT_CLIENT_SECRET="$MONITORING_AGENT_CLIENT_SECRET" \
          --build-arg SENTRIUS_RDPPROXY_CLIENT_SECRET="$SENTRIUS_RDPPROXY_CLIENT_SECRET" \
          --build-arg PROMPT_ADVISOR_CLIENT_SECRET="$PROMPT_ADVISOR_CLIENT_SECRET" \
          "$context_dir"
    else
        docker build "${BUILD_ARGS[@]}" -t "$name:$version" \
          --build-arg SENTRIUS_API_CLIENT_SECRET="$SENTRIUS_API_CLIENT_SECRET" \
          --build-arg SENTRIUS_APROXY_CLIENT_SECRET="$SENTRIUS_APROXY_CLIENT_SECRET" \
          --build-arg SENTRIUS_LAUNCHER_CLIENT_SECRET="$SENTRIUS_LAUNCHER_CLIENT_SECRET" \
          --build-arg JAVA_AGENTS_CLIENT_SECRET="$JAVA_AGENTS_CLIENT_SECRET" \
          --build-arg MONITORING_AGENT_CLIENT_SECRET="$MONITORING_AGENT_CLIENT_SECRET" \
          --build-arg SENTRIUS_RDPPROXY_CLIENT_SECRET="$SENTRIUS_RDPPROXY_CLIENT_SECRET" \
          --build-arg PROMPT_ADVISOR_CLIENT_SECRET="$PROMPT_ADVISOR_CLIENT_SECRET" \
          "$context_dir"
    fi

    if [ $? -ne 0 ]; then
        echo "❌ Failed to build $name"
        cleanup_docker_context "$context_dir"
        exit 1
    fi

    # Sync image into Minikube if running multi-node
    if [[ "$ENV_TARGET" == "local" && "$USE_MINIKUBE_LOAD" == true ]]; then
        echo "📦 Loading $name:$version into Minikube nodes..."
        minikube image load "$name:$version"
    fi

    if [[ "$ENV_TARGET" == "gcp" ]]; then
        REGISTRY="us-central1-docker.pkg.dev/sentrius-project/sentrius-repo"
        docker tag "$name:$version" "$REGISTRY/$name:$version"
        docker push "$REGISTRY/$name:$version"
        echo "✅ Pushed $REGISTRY/$name:$version"
    else
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
update_ssh_proxy=false
update_rdp_proxy=false
update_github_mcp=false
update_prompt_advisor=false


while [[ "$#" -gt 0 ]]; do
    case $1 in
        --sentrius) update_sentrius=true ;;
        --sentrius-ssh) update_sentrius_ssh=true ;;
        --sentrius-keycloak) update_sentrius_keycloak=true ;;
        --sentrius-agent) update_sentrius_agent=true ;;
        --sentrius-monitoring-agent) update_sentrius_monitoring_agent=true ;;
        --sentrius-ssh-agent) update_sentrius_ssh_agent=true ;;
        --sentrius-ai-agent) update_sentrius_ai_agent=true ;;
        --sentrius-launcher-service) update_launcher=true ;;
        --sentrius-integration-proxy) update_integrationproxy=true ;;
        --sentrius-agent-proxy) update_agent_proxy=true ;;
        --sentrius-ssh-proxy) update_ssh_proxy=true ;;
        --sentrius-rdp-proxy) update_rdp_proxy=true ;;
        --github-mcp-server) update_github_mcp=true ;;
        --prompt-advisor) update_prompt_advisor=true ;;
        --all) update_sentrius=true; update_sentrius_ssh=true; update_sentrius_keycloak=true; update_sentrius_agent=true; update_sentrius_ai_agent=true; update_integrationproxy=true; update_launcher=true; update_agent_proxy=true; update_ssh_proxy=true; update_rdp_proxy=true; update_github_mcp=true; update_prompt_advisor=true; update_sentrius_monitoring_agent=true; update_sentrius_ssh_agent=true;;
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
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        SENTRIUS_VERSION=$(increment_patch_version $SENTRIUS_VERSION)
        update_env_var "SENTRIUS_VERSION" "$SENTRIUS_VERSION"
    else
        SENTRIUS_VERSION="latest"
    fi
    build_image "sentrius" "$SENTRIUS_VERSION" "${SCRIPT_DIR}/../../docker/sentrius/"
    rm docker/sentrius/sentrius.jar
fi

if $update_sentrius_ssh; then
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        SENTRIUS_SSH_VERSION=$(increment_patch_version $SENTRIUS_SSH_VERSION)
        update_env_var "SENTRIUS_SSH_VERSION" "$SENTRIUS_SSH_VERSION"
    else
        SENTRIUS_SSH_VERSION="latest"
    fi
    build_image "sentrius-ssh" "$SENTRIUS_SSH_VERSION" "${SCRIPT_DIR}/../../docker/fake-ssh"
fi

if $update_sentrius_keycloak; then
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        SENTRIUS_KEYCLOAK_VERSION=$(increment_patch_version $SENTRIUS_KEYCLOAK_VERSION)
        update_env_var "SENTRIUS_KEYCLOAK_VERSION" "$SENTRIUS_KEYCLOAK_VERSION"
    else
        SENTRIUS_KEYCLOAK_VERSION="latest"
    fi
    build_keycloak_image "sentrius-keycloak" "$SENTRIUS_KEYCLOAK_VERSION" "${SCRIPT_DIR}/../../docker/keycloak"
fi

if $update_sentrius_agent; then
    cp analytics/target/analytics-*.jar docker/sentrius-agent/agent.jar
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        SENTRIUS_AGENT_VERSION=$(increment_patch_version $SENTRIUS_AGENT_VERSION)
        update_env_var "SENTRIUS_AGENT_VERSION" "$SENTRIUS_AGENT_VERSION"
    else
        SENTRIUS_AGENT_VERSION="latest"
    fi
    build_image "sentrius-agent" "$SENTRIUS_AGENT_VERSION" "${SCRIPT_DIR}/../../docker/sentrius-agent"
    rm docker/sentrius-agent/agent.jar
fi

if $update_sentrius_ai_agent; then
    cp enterprise-agent/target/enterprise-agent-*.jar docker/sentrius-ai-agent/agent.jar
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        SENTRIUS_AI_AGENT_VERSION=$(increment_patch_version $SENTRIUS_AI_AGENT_VERSION)
        update_env_var "SENTRIUS_AI_AGENT_VERSION" "$SENTRIUS_AI_AGENT_VERSION"
    else
        SENTRIUS_AI_AGENT_VERSION="latest"
    fi
    build_image "sentrius-ai-agent" "$SENTRIUS_AI_AGENT_VERSION" "${SCRIPT_DIR}/../../docker/sentrius-ai-agent"
    rm docker/sentrius-ai-agent/agent.jar

    cp enterprise-agent/target/enterprise-agent-*.jar docker/sentrius-launchable-agent/agent.jar
    build_image "sentrius-launchable-agent" "$SENTRIUS_AI_AGENT_VERSION" "${SCRIPT_DIR}/../../docker/sentrius-launchable-agent"
    rm docker/sentrius-launchable-agent/agent.jar
fi

if $update_integrationproxy; then
    cp integration-proxy/target/sentrius-integration-proxy-*.jar docker/integrationproxy/llmproxy.jar
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        LLMPROXY_VERSION=$(increment_patch_version $LLMPROXY_VERSION)
        update_env_var "LLMPROXY_VERSION" "$LLMPROXY_VERSION"
    else
        LLMPROXY_VERSION="latest"
    fi
    build_image "sentrius-integration-proxy" "$LLMPROXY_VERSION" "${SCRIPT_DIR}/../../docker/integrationproxy"
    rm docker/integrationproxy/llmproxy.jar
fi

if $update_launcher; then
    cp agent-launcher/target/agent-launcher-*.jar docker/sentrius-launcher-service/launcher.jar
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        LAUNCHER_VERSION=$(increment_patch_version $LAUNCHER_VERSION)
        update_env_var "LAUNCHER_VERSION" "$LAUNCHER_VERSION"
    else
        LAUNCHER_VERSION="latest"
    fi
    build_image "sentrius-launcher-service" "$LAUNCHER_VERSION" "${SCRIPT_DIR}/../../docker/sentrius-launcher-service"
    rm docker/sentrius-launcher-service/launcher.jar
fi

if $update_agent_proxy; then
    cp agent-proxy/target/sentrius-agent-proxy-*.jar docker/agent-proxy/agentproxy.jar
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        AGENTPROXY_VERSION=$(increment_patch_version $AGENTPROXY_VERSION)
        update_env_var "AGENTPROXY_VERSION" "$AGENTPROXY_VERSION"
    else
        AGENTPROXY_VERSION="latest"
    fi
    build_image "sentrius-agent-proxy" "$AGENTPROXY_VERSION" "${SCRIPT_DIR}/../../docker/agent-proxy"
    rm docker/agent-proxy/agentproxy.jar
fi

if $update_ssh_proxy; then
    cp ssh-proxy/target/ssh-proxy-*.jar docker/ssh-proxy/sshproxy.jar
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        SSHPROXY_VERSION=$(increment_patch_version $SSHPROXY_VERSION)
        update_env_var "SSHPROXY_VERSION" "$SSHPROXY_VERSION"
    else
        SSHPROXY_VERSION="latest"
    fi
    build_image "sentrius-ssh-proxy" "$SSHPROXY_VERSION" "${SCRIPT_DIR}/../../docker/ssh-proxy"
    rm docker/ssh-proxy/sshproxy.jar
fi

if $update_rdp_proxy; then
    cp rdp-proxy/target/rdp-proxy-*.jar docker/rdp-proxy/rdpproxy.jar
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        RDPPROXY_VERSION=$(increment_patch_version $RDPPROXY_VERSION)
        update_env_var "RDPPROXY_VERSION" "$RDPPROXY_VERSION"
    else
        RDPPROXY_VERSION="latest"
    fi
    build_image "sentrius-rdp-proxy" "$RDPPROXY_VERSION" "${SCRIPT_DIR}/../../docker/rdp-proxy"
    rm docker/rdp-proxy/rdpproxy.jar
fi

if $update_monitoring_agent; then
    cp monitoring/target/monitoring-*.jar docker/monitoring/monitoring.jar
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        $MONITORING_AGENT_VERSION=$(increment_patch_version $MONITORING_AGENT_VERSION)
        update_env_var "$MONITORING_AGENT_VERSION" "$MONITORING_AGENT_VERSION"
    else
        $MONITORING_AGENT_VERSION="latest"
    fi
    build_image "sentrius-monitoring-agent" "$MONITORING_AGENT_VERSION" "${SCRIPT_DIR}/../../docker/monitoring"
    rm docker/monitoring/monitoring.jar
fi

if $update_ssh_agent; then
    cp \/target/ssh-agent-*.jar docker/ssh-agent/ssh-agent.jar
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        $SSH_AGENT_VERSION=$(increment_patch_version $SSH_AGENT_VERSION)
        update_env_var "$SSH_AGENT_VERSION" "$SSH_AGENT_VERSION"
    else
        $SSH_AGENT_VERSION="latest"
    fi
    build_image "sentrius-ssh-agent" "$SSH_AGENT_VERSION" "${SCRIPT_DIR}/../../docker/ssh-agent"
    rm docker/ssh-agent/ssh-agent.jar
fi

if $update_github_mcp; then
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        GITHUB_MCP_VERSION=$(increment_patch_version $GITHUB_MCP_VERSION)
        update_env_var "GITHUB_MCP_VERSION" "$GITHUB_MCP_VERSION"
    else
        GITHUB_MCP_VERSION="latest"
    fi
    build_image "github-mcp-server" "$GITHUB_MCP_VERSION" "${SCRIPT_DIR}/../../docker/github-mcp-server"
fi

if $update_prompt_advisor; then
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        PROMPT_ADVISOR_VERSION=$(increment_patch_version $PROMPT_ADVISOR_VERSION)
        update_env_var "PROMPT_ADVISOR_VERSION" "$PROMPT_ADVISOR_VERSION"
    else
        PROMPT_ADVISOR_VERSION="latest"
    fi
    build_image "sentrius-prompt-advisor" "$PROMPT_ADVISOR_VERSION" "${SCRIPT_DIR}/../../" -f Dockerfile-prompt-advisor --skip-dev-certs
fi

if $update_prompt_advisor_token_refresher; then
    if [[ "$ENV_TARGET" == "gcp" ]]; then
        PROMPT_ADVISOR_TOKEN_REFRESHER_VERSION=$(increment_patch_version $PROMPT_ADVISOR_TOKEN_REFRESHER_VERSION)
        update_env_var "PROMPT_ADVISOR_TOKEN_REFRESHER_VERSION" "$PROMPT_ADVISOR_TOKEN_REFRESHER_VERSION"
    else
        PROMPT_ADVISOR_TOKEN_REFRESHER_VERSION="latest"
    fi
    build_image "sentrius-prompt-advisor-token-refresher" "$PROMPT_ADVISOR_TOKEN_REFRESHER_VERSION" "${SCRIPT_DIR}/../../docker/prompt-advisor-token-refresher" --skip-dev-certs
fi