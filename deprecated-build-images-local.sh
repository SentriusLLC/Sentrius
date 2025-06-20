#!/bin/bash

# Load environment variables
source .env

cp .env .env.bak

eval $(minikube -p minikube docker-env)
# Update the specific variables in the .env file
update_env_var() {
    local key=$1
    local value=$2
    if grep -q "^$key=" .env; then
        # Replace the existing variable
        sed -i "s/^$key=.*/$key=$value/" .env
    else
        # Append the new variable if it doesn't exist
        echo "$key=$value" >> .env
    fi
}


increment_patch_version() {
  version=$1
  major=$(echo "$version" | cut -d. -f1)
  minor=$(echo "$version" | cut -d. -f2)
  patch=$(echo "$version" | cut -d. -f3)
  new_patch=$((patch + 1))
  echo "$major.$minor.$new_patch"
}

# Function to build a Docker image with optional --no-cache
build_image() {
    local name=$1
    local version=$2
    local context_dir=$3

    echo "Building $name:$version..."

    # Use --no-cache if the flag is set
    if $no_cache; then
        docker build --no-cache -t "$name:$version" "$context_dir"
    else
        docker build -t "$name:$version" "$context_dir"
    fi

    if [ $? -ne 0 ]; then
        echo "Failed to build $name"
        exit 1
    fi

    echo "Successfully built $name:$version"
}


# Parse flags
update_sentrius=false
update_sentrius_ssh=false
update_sentrius_keycloak=false
update_sentrius_agent=false
update_sentrius_ai_agent=false
update_llmproxy=false
no_cache=false  # Default: use cache


while [[ "$#" -gt 0 ]]; do
    case $1 in
        --sentrius) update_sentrius=true ;;
        --sentrius-ssh) update_sentrius_ssh=true ;;
        --sentrius-keycloak) update_sentrius_keycloak=true ;;
        --sentrius-agent) update_sentrius_agent=true ;;
        --sentrius-ai-agent) update_sentrius_ai_agent=true ;;
        --sentrius-llmproxy) update_llmproxy=true ;;
        --all) update_sentrius=true; update_sentrius_ssh=true; update_sentrius_keycloak=true; update_sentrius_agent=true; update_sentrius_ai_agent=true; update_llmproxy=true ;;
        --no-cache) no_cache=true ;;  # Set no_cache to true if the flag is passed
        *) echo "Unknown flag: $1"; exit 1 ;;
    esac
    shift
done

# Build selected images
if $update_sentrius; then
    SENTRIUS_VERSION=$(increment_patch_version $SENTRIUS_VERSION)
    build_image "sentrius" "$SENTRIUS_VERSION" "."
    update_env_var "SENTRIUS_VERSION" "$SENTRIUS_VERSION"
    ## for local, replace minikube with docker
    echo "Loading image into minikube"
    #minikube image load sentrius:$SENTRIUS_VERSION
fi

if $update_sentrius_ssh; then
    SENTRIUS_SSH_VERSION=$(increment_patch_version $SENTRIUS_SSH_VERSION)
    build_image "sentrius-ssh" "$SENTRIUS_SSH_VERSION" "./docker/fake-ssh/"
    update_env_var "SENTRIUS_SSH_VERSION" "$SENTRIUS_SSH_VERSION"
    ## for local, replace minikube with docker
    echo "Loading image into minikube"
    #minikube image load sentrius-ssh:$SENTRIUS_SSH_VERSION
fi

if $update_sentrius_keycloak; then
    SENTRIUS_KEYCLOAK_VERSION=$(increment_patch_version $SENTRIUS_KEYCLOAK_VERSION)
    build_image "sentrius-keycloak" "$SENTRIUS_KEYCLOAK_VERSION" "./docker/keycloak"
    update_env_var "SENTRIUS_KEYCLOAK_VERSION" "$SENTRIUS_KEYCLOAK_VERSION"
    ## for local, replace minikube with docker
    echo "Loading image into minikube"
    #minikube image load sentrius-keycloak:$SENTRIUS_KEYCLOAK_VERSION
fi

if $update_sentrius_agent; then
    cp analytics/target/analytics-*.jar docker/sentrius-agent/agent.jar
    SENTRIUS_AGENT_VERSION=$(increment_patch_version $SENTRIUS_AGENT_VERSION)
    build_image "sentrius-agent" "$SENTRIUS_AGENT_VERSION" "./docker/sentrius-agent"
    rm docker/sentrius-agent/agent.jar
    update_env_var "SENTRIUS_AGENT_VERSION" "$SENTRIUS_AGENT_VERSION"
    ## for local, replace minikube with docker
    docker tag sentrius-agent:$SENTRIUS_AGENT_VERSION sentrius-agent:latest
    echo "Loading image into minikube"
    #minikube image load sentrius-agent:$SENTRIUS_AGENT_VERSION
    #minikube image load sentrius-agent:latest
fi

if $update_sentrius_ai_agent; then
    cp ai-agent/target/ai-agent-*.jar docker/sentrius-ai-agent/agent.jar
    SENTRIUS_AI_AGENT_VERSION=$(increment_patch_version $SENTRIUS_AI_AGENT_VERSION)
    build_image "sentrius-ai-agent" "$SENTRIUS_AI_AGENT_VERSION" "./docker/sentrius-ai-agent"
    rm docker/sentrius-ai-agent/agent.jar
    update_env_var "SENTRIUS_AI_AGENT_VERSION" "$SENTRIUS_AI_AGENT_VERSION"
    ## for local, replace minikube with docker
    docker tag sentrius-ai-agent:$SENTRIUS_AI_AGENT_VERSION sentrius-ai-agent:latest

    cp ai-agent/target/ai-agent-*.jar docker/sentrius-ai-agent/agent.jar

    build_image "sentrius-launchable-agent" "$SENTRIUS_AI_AGENT_VERSION" "./docker/sentrius-launchable-agent"
    rm docker/sentrius-launchable-agent/agent.jar
    ## for local, replace minikube with docker
    docker tag sentrius-launchable-agent:$SENTRIUS_AI_AGENT_VERSION sentrius-launchable-agent:latest

    echo "Loading image into minikube"
    #minikube image load sentrius-ai-agent:$SENTRIUS_AI_AGENT_VERSION
    #minikube image load sentrius-ai-agent:latest
fi

if $update_llmproxy; then
    cp llm-proxy/target/sentrius-llm-proxy-*.jar docker/llmproxy/llmproxy.jar
    LLMPROXY_VERSION=$(increment_patch_version $LLMPROXY_VERSION)
    build_image "sentrius-llmproxy" "$LLMPROXY_VERSION" "./docker/llmproxy"
    rm docker/llmproxy/llmproxy.jar
    update_env_var "LLMPROXY_VERSION" "$LLMPROXY_VERSION"
    ## for local, replace minikube with docker
    docker tag sentrius-llmproxy:$LLMPROXY_VERSION sentrius-llmproxy:latest
    echo "Loading image into minikube"
    #minikube image load sentrius-ai-agent:LLMPROXY_VERSION
    #minikube image load sentrius-ai-agent:latest
fi