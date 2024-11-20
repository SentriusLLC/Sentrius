#!/bin/bash

# Load environment variables
source .env

cp .env .env.bak

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

# Function to build and tag a Docker image
build_image() {
    local name=$1
    local version=$2
    local context_dir=$3

    echo "Building $name:$version..."
    docker build -t "$name:$version" "$context_dir" || { echo "Failed to build $name"; exit 1; }
    echo "Successfully built $name:$version"
    echo docker tag "$name:$version" 060808646119.dkr.ecr.us-east-1.amazonaws.com/$name:$version
    docker tag "$name:$version" 060808646119.dkr.ecr.us-east-1.amazonaws.com/$name:$version
    echo docker push 060808646119.dkr.ecr.us-east-1.amazonaws.com/$name:$version
    docker push 060808646119.dkr.ecr.us-east-1.amazonaws.com/$name:$version
}


# Parse flags
update_sentrius=false
update_sentrius_ssh=false
update_sentrius_keycloak=false
update_session_agent=false

while [[ "$#" -gt 0 ]]; do
    case $1 in
        --sentrius) update_sentrius=true ;;
        --sentrius-ssh) update_sentrius_ssh=true ;;
        --sentrius-keycloak) update_sentrius_keycloak=true ;;
        --all) update_sentrius=true; update_sentrius_ssh=true; update_sentrius_keycloak=true ;;
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
fi

if $update_sentrius_ssh; then
    SENTRIUS_SSH_VERSION=$(increment_patch_version $SENTRIUS_SSH_VERSION)
    build_image "sentrius-ssh" "$SENTRIUS_SSH_VERSION" "./docker/fake-ssh"
    update_env_var "SENTRIUS_SSH_VERSION" "$SENTRIUS_SSH_VERSION"
    ## for local, replace minikube with docker
fi

if $update_sentrius_keycloak; then
    SENTRIUS_KEYCLOAK_VERSION=$(increment_patch_version $SENTRIUS_KEYCLOAK_VERSION)
    build_image "sentrius-keycloak" "$SENTRIUS_KEYCLOAK_VERSION" "./docker/keycloak"
    update_env_var "SENTRIUS_KEYCLOAK_VERSION" "$SENTRIUS_KEYCLOAK_VERSION"
    ## for local, replace minikube with docker
fi
