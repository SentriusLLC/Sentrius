#!/bin/bash
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh

TENANT=$1

if [[ -z "$TENANT" ]]; then
    echo "Usage: $0 <tenant-name>" 1>&2
    exit 1
fi

echo "Removing DNS records for tenant ${TENANT}..."

# Start DNS transaction
gcloud dns record-sets transaction start --zone=${ZONE}

# Retrieve DNS record details
TENANT_RECORD=$(gcloud dns record-sets list --zone=${ZONE} --name=${TENANT}.sentrius.cloud. --format="value(rrdatas[0],ttl,type)")
KEYCLOAK_RECORD=$(gcloud dns record-sets list --zone=${ZONE} --name=keycloak.${TENANT}.sentrius.cloud. --format="value(rrdatas[0],ttl,type)")
AGENTPROXY_RECORD=$(gcloud dns record-sets list --zone=${ZONE} --name=agentproxy.${TENANT}.sentrius.cloud. --format="value(rrdatas[0],ttl,type)")
RDPPROXY_RECORD=$(gcloud dns record-sets list --zone=${ZONE} --name=rdpproxy.${TENANT}.sentrius.cloud. --format="value(rrdatas[0],ttl,type)")

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

# Delete Agent Proxy DNS record
if [[ -n "$AGENTPROXY_RECORD" ]]; then
    read -r AGENTPROXY_RRDATA AGENTPROXY_TTL AGENTPROXY_TYPE <<< "$AGENTPROXY_RECORD"
    gcloud dns record-sets transaction remove --zone=${ZONE} \
        --name=agentproxy.${TENANT}.sentrius.cloud. \
        --type=$AGENTPROXY_TYPE \
        --ttl=$AGENTPROXY_TTL \
        $AGENTPROXY_RRDATA || echo "Failed to remove DNS record for agentproxy.${TENANT}.sentrius.cloud"
else
    echo "No DNS record found for agentproxy.${TENANT}.sentrius.cloud"
fi

# Delete RDP Proxy DNS record
if [[ -n "$RDPPROXY_RECORD" ]]; then
    read -r RDPPROXY_RRDATA RDPPROXY_TTL RDPPROXY_TYPE <<< "$RDPPROXY_RECORD"
    gcloud dns record-sets transaction remove --zone=${ZONE} \
        --name=rdpproxy.${TENANT}.sentrius.cloud. \
        --type=$RDPPROXY_TYPE \
        --ttl=$RDPPROXY_TTL \
        $RDPPROXY_RRDATA || echo "Failed to remove DNS record for rdpproxy.${TENANT}.sentrius.cloud"
else
    echo "No DNS record found for rdpproxy.${TENANT}.sentrius.cloud"
fi

# Execute the DNS record transaction
gcloud dns record-sets transaction execute --zone=${ZONE} || echo "No DNS changes applied."

echo "✅ DNS records removed for tenant ${TENANT}"
