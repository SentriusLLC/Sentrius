#!/bin/bash

# Load environment variables
source .gcp.env

cp .gcp.env .gcp.env.bak

# Update the specific variables in the .gcp.env file
update_env_var() {
    local key=$1
    local value=$2
    if grep -q "^$key=" .gcp.env; then
        # Replace the existing variable
        sed -i "s/^$key=.*/$key=$value/" .gcp.env
    else
        # Append the new variable if it doesn't exist
        echo "$key=$value" >> .gcp.env
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

# Function to build, tag, and push a Docker image
build_and_push_image() {
    local name=$1
    local version=$2
    local context_dir=$3

    local repo="us-central1-docker.pkg.dev/sentrius-project/sentrius-repo"

    echo "Building $name:$version..."
    docker build -t "$name:$version" "$context_dir" || { echo "Failed to build $name"; exit 1; }

    echo "Tagging and pushing $name:$version to $repo..."
    docker tag "$name:$version" "$repo/$name:$version"
    docker push "$repo/$name:$version" || { echo "Failed to push $name:$version"; exit 1; }

    echo "Successfully pushed $repo/$name:$version"
}

# Parse flags
update_sentrius=false
update_sentrius_ssh=false
update_sentrius_keycloak=false
update_sentrius_agent=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --sentrius) update_sentrius=true ;;
        --sentrius-ssh) update_sentrius_ssh=true ;;
        --sentrius-keycloak) update_sentrius_keycloak=true ;;
        --sentrius-agent) update_sentrius_agent=true ;;
        --all) update_sentrius=true; update_sentrius_ssh=true; update_sentrius_keycloak=true; update_sentrius_agent=true ;;
        *) echo "Unknown flag: $1"; exit 1 ;;
    esac
    shift
done

# Authenticate with Google Artifact Registry
echo "Authenticating with Google Cloud..."
gcloud auth configure-docker us-central1-docker.pkg.dev || { echo "Failed to authenticate with Artifact Registry"; exit 1; }

# Build and push selected images
if $update_sentrius; then
    SENTRIUS_VERSION=$(increment_patch_version $SENTRIUS_VERSION)
    build_and_push_image "sentrius" "$SENTRIUS_VERSION" "."
    update_env_var "SENTRIUS_VERSION" "$SENTRIUS_VERSION"
fi

if $update_sentrius_ssh; then
    SENTRIUS_SSH_VERSION=$(increment_patch_version $SENTRIUS_SSH_VERSION)
    build_and_push_image "sentrius-ssh" "$SENTRIUS_SSH_VERSION" "./docker/fake-ssh"
    update_env_var "SENTRIUS_SSH_VERSION" "$SENTRIUS_SSH_VERSION"
fi

if $update_sentrius_keycloak; then
    SENTRIUS_KEYCLOAK_VERSION=$(increment_patch_version $SENTRIUS_KEYCLOAK_VERSION)
    build_and_push_image "sentrius-keycloak" "$SENTRIUS_KEYCLOAK_VERSION" "./docker/keycloak"
    update_env_var "SENTRIUS_KEYCLOAK_VERSION" "$SENTRIUS_KEYCLOAK_VERSION"
fi

if $update_sentrius_agent; then
    cp java-agents/target/java-agent-*.jar docker/sentrius-agent/agent.jar
    SENTRIUS_AGENT_VERSION=$(increment_patch_version $SENTRIUS_AGENT_VERSION)
    build_and_push_image "sentrius-agent" "$SENTRIUS_AGENT_VERSION" "./docker/sentrius-agent"
    rm docker/sentrius-agent/agent.jar
    update_env_var "SENTRIUS_AGENT_VERSION" "$SENTRIUS_AGENT_VERSION"
fi
