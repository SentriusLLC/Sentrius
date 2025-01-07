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
    echo "Namespace ${TENANT} does not exist. Nothing to delete."
else
    echo "Deleting Kubernetes namespace ${TENANT}..."
    kubectl delete namespace ${TENANT} --wait || echo "Failed to delete namespace ${TENANT}"
fi

# Uninstall Helm release
echo "Uninstalling Helm release for tenant ${TENANT}..."
helm uninstall sentrius --namespace ${TENANT} || echo "Helm release not found for tenant ${TENANT}"

# Delete DNS records
echo "Deleting DNS records for tenant ${TENANT}..."
gcloud dns record-sets transaction start --zone=${ZONE}

# Retrieve DNS record details
TENANT_RECORD=$(gcloud dns record-sets list --zone=${ZONE} --name=${TENANT}.sentrius.cloud. --format="value(rrdatas[0],ttl,type)")
KEYCLOAK_RECORD=$(gcloud dns record-sets list --zone=${ZONE} --name=keycloak.${TENANT}.sentrius.cloud. --format="value(rrdatas[0],ttl,type)")

# Delete tenant DNS record
if [[ -n "$TENANT_RECORD" ]]; then
    read -r TENANT_RRDATA TENANT_TTL TENANT_TYPE <<< "$TENANT_RECORD"
    gcloud dns record-sets transaction remove --zone=${ZONE} \
        --name=${TENANT}.sentrius.cloud. \
        --type=$TENANT_TYPE \
        --ttl=$TENANT_TTL \
        $TENANT_RRDATA || echo "Failed to remove DNS record for ${TENANT}.sentrius.cloud"
else
    echo "No DNS record found for ${TENANT}.sentrius.cloud"
fi

# Delete Keycloak DNS record
if [[ -n "$KEYCLOAK_RECORD" ]]; then
    read -r KEYCLOAK_RRDATA KEYCLOAK_TTL KEYCLOAK_TYPE <<< "$KEYCLOAK_RECORD"
    gcloud dns record-sets transaction remove --zone=${ZONE} \
        --name=keycloak.${TENANT}.sentrius.cloud. \
        --type=$KEYCLOAK_TYPE \
        --ttl=$KEYCLOAK_TTL \
        $KEYCLOAK_RRDATA || echo "Failed to remove DNS record for keycloak.${TENANT}.sentrius.cloud"
else
    echo "No DNS record found for keycloak.${TENANT}.sentrius.cloud"
fi

# Execute the DNS record transaction
gcloud dns record-sets transaction execute --zone=${ZONE} || echo "No DNS changes applied."

echo "All resources for tenant ${TENANT} have been deleted."
