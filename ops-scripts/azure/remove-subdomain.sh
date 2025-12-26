#!/bin/bash
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh

TENANT=$1

if [[ -z "$TENANT" ]]; then
    echo "Usage: $0 <tenant-name>" 1>&2
    exit 1
fi

echo "Removing DNS records for tenant ${TENANT}..."

# Remove main tenant domain
az network dns record-set a delete \
    --resource-group ${RESOURCE_GROUP} \
    --zone-name ${DNS_ZONE} \
    --name ${TENANT} \
    --yes 2>/dev/null || echo "  ${TENANT}.${DNS_ZONE} not found"

# Remove Keycloak subdomain
az network dns record-set a delete \
    --resource-group ${RESOURCE_GROUP} \
    --zone-name ${DNS_ZONE} \
    --name keycloak.${TENANT} \
    --yes 2>/dev/null || echo "  keycloak.${TENANT}.${DNS_ZONE} not found"

# Remove Agent Proxy subdomain
az network dns record-set a delete \
    --resource-group ${RESOURCE_GROUP} \
    --zone-name ${DNS_ZONE} \
    --name agentproxy.${TENANT} \
    --yes 2>/dev/null || echo "  agentproxy.${TENANT}.${DNS_ZONE} not found"

# Remove RDP Proxy subdomain
az network dns record-set a delete \
    --resource-group ${RESOURCE_GROUP} \
    --zone-name ${DNS_ZONE} \
    --name rdpproxy.${TENANT} \
    --yes 2>/dev/null || echo "  rdpproxy.${TENANT}.${DNS_ZONE} not found"

echo "✅ DNS records removed successfully!"
