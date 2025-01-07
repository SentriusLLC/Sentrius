#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)


source ${SCRIPT_DIR}/base.sh
source ${SCRIPT_DIR}/../../.gcp.env

TENANT=$1

if [[ -z "$TENANT" ]]; then
    echo "Must provide single argument for tenant name" 1>&2
    exit 1
fi

# Check if namespace exists
kubectl get namespace ${TENANT} >/dev/null 2>&1
if [[ $? -ne 0 ]]; then
    echo "Namespace ${TENANT} does not exist. Creating..."
    kubectl create namespace ${TENANT} || { echo "Failed to create namespace ${TENANT}"; exit 1; }
fi



helm upgrade --install sentrius ./sentrius-gcp-chart --namespace ${TENANT} \
    --set tenant=${TENANT} \
    --set subdomain=${TENANT}.sentrius.cloud \
    --set sentrius.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius \
    --set sentrius.image.tag=${SENTRIUS_VERSION} \
    --set ssh.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-ssh \
    --set ssh.image.tag=${SENTRIUS_SSH_VERSION} \
    --set keycloak.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-keycloak \
    --set keycloak.image.tag=${SENTRIUS_KEYCLOAK_VERSION} \
    --set sentriusagent.image.repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius-agent \
    --set sentriusagent.image.tag=${SENTRIUS_AGENT_VERSION} || { echo "Failed to deploy Sentrius with Helm"; exit 1; }


# Wait for LoadBalancer IPs to be ready
echo "Waiting for LoadBalancer IPs to be assigned..."
RETRIES=30
SLEEP_INTERVAL=10

for ((i=1; i<=RETRIES; i++)); do
    # Retrieve LoadBalancer IP
    # Retrieve LoadBalancer IP
    INGRESS_IP=$(kubectl get ingress managed-cert-ingress-${TENANT} -n ${TENANT} -o jsonpath='{.status.loadBalancer.ingress[0].ip}')


    if [[ -n "$INGRESS_IP" ]]; then
        echo "INGRESS_IP IP: $INGRESS_IP"
        break
    fi

    echo "Attempt $i: Waiting for IPs to be assigned..."
    sleep $SLEEP_INTERVAL
done

if [[ -z "$INGRESS_IP" ]]; then
    echo "Failed to retrieve LoadBalancer IPs after $((RETRIES * SLEEP_INTERVAL)) seconds."
    exit 1
fi

# Check if subdomain exists
if gcloud dns record-sets list --zone=${ZONE} --name=${TENANT}.sentrius.cloud. | grep -q ${TENANT}.sentrius.cloud.; then
    echo "Subdomain ${TENANT}.sentrius.cloud already exists. Skipping creation."
else
    echo "Creating subdomain ${TENANT}.sentrius.cloud..."
    gcloud dns record-sets transaction start --zone=${ZONE}

    gcloud dns record-sets transaction add --zone=${ZONE} \
          --name=${TENANT}.sentrius.cloud. \
          --type=A \
          --ttl=300 \
          $INGRESS_IP

    gcloud dns record-sets transaction add --zone=${ZONE} \
      --name=keycloak.${TENANT}.sentrius.cloud. \
      --type=A \
      --ttl=300 \
      $INGRESS_IP

    gcloud dns record-sets transaction execute --zone=${ZONE}
fi