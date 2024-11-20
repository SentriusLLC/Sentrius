#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)


source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.gcp.env

kubectl scale deployment --all --replicas=1 -n sentrius


helm upgrade --install sentrius ./sentrius-gcp-chart --namespace ${NAMESPACE} \
    --set sentrius.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set ssh.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-ssh \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set keycloak.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-keycloak \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set sentriusagent.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-agent \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }
