#!/usr/bin/env bash

##
# run-chat-agent.sh
#
# Simple script to launch Sentrius locally using Maven and environment variables.
#
# Usage:
#   ./run-chat-agent.sh <agent-name> [--build]
##

# Fail on any error
set -e

######################################
# 0. Parse script arguments
######################################
BUILD=false
AGENT_NAME=""

while [[ "$#" -gt 0 ]]; do
  case $1 in
    --build)
      BUILD=true
      ;;
    -*)
      echo "Unknown parameter passed: $1"
      exit 1
      ;;
    *)
      if [ -z "$AGENT_NAME" ]; then
        AGENT_NAME="$1"
      else
        echo "Multiple agent names provided: '$AGENT_NAME' and '$1'"
        exit 1
      fi
      ;;
  esac
  shift
done

if [ -z "$AGENT_NAME" ]; then
  echo "❌ Error: You must provide an agent name."
  echo "Usage: ./run-chat-agent.sh <agent-name> [--build]"
  exit 1
fi

######################################
# 1. (Optional) Build the project
######################################
if $BUILD; then
  echo "Building the project..."
  mvn clean install
fi

######################################
# 2. Set environment variables
######################################

export KEYCLOAK_SECRET="${KEYCLOAK_SECRET:-defaultSecret}"
export KEYCLOAK_BASE_URL="${KEYCLOAK_BASE_URL:-http://localhost:8180}"
export HTTP_REQUIRED="${HTTP_REQUIRED:-false}"
export BASE_URL="${BASE_URL:-http://localhost:8080}"
export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4317}"

export MIN_HEAP="${MIN_HEAP:-4096m}"
export MAX_HEAP="${MAX_HEAP:-8192m}"

######################################
# 3. Run Maven
######################################

pushd ai-agent

mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Xms${MIN_HEAP} -Xmx${MAX_HEAP}" \
  -Dspring-boot.run.arguments="--spring.config.location=file:./src/main/resources/chat-helper.properties --agent.ai.config=chat-helper.yaml --agent.namePrefix=${AGENT_NAME}"

popd
