#!/usr/bin/env bash

##
# run-sentrius.sh
#
# Simple script to launch Sentrius locally using Maven and environment variables.
#
# Usage:
#   ./run-sentrius.sh
#
# You can export environment variables before calling this script or define them inline below.
##

# Fail on any error
set -e

######################################
# 0. Parse script arguments
######################################
BUILD=false

while [[ "$#" -gt 0 ]]; do
  case $1 in
    --build)
      BUILD=true
      ;;
    *)
      echo "Unknown parameter passed: $1"
      exit 1
      ;;
  esac
  shift
done

######################################
# 1. (Optional) Build the project
######################################
if $BUILD; then
  echo "Building the project..."
  # Build from the root. If you only want to build the 'api' module, you can use:
  #   mvn clean install -pl api -am
  mvn clean install
fi

######################################
# 2. Set environment variables here
######################################

# You can set these externally (e.g., via `export KEYCLOAK_SECRET=...`),
# or define them right here:
export KEYCLOAK_SECRET="${KEYCLOAK_SECRET:-defaultSecret}"
export KEYCLOAK_BASE_URL="${KEYCLOAK_BASE_URL:-http://localhost:8180}"
export HTTP_REQUIRED="${HTTP_REQUIRED:-false}"
export BASE_URL="${BASE_URL:-http://localhost:8080}"

# Adjust memory settings for your local environment
export MIN_HEAP="${MIN_HEAP:-4096m}"
export MAX_HEAP="${MAX_HEAP:-8192m}"

######################################
# 3. Run Maven with these settings
######################################

# build the project

pushd api

mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Xms${MIN_HEAP} -Xmx${MAX_HEAP}"

# Explanation:
#   -Xms${MIN_HEAP} sets the initial (minimum) heap to 4GB (by default).
#   -Xmx${MAX_HEAP} sets the maximum heap to 8GB (by default).

popd