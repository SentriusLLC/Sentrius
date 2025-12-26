#!/bin/bash
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh

TENANT=$1
INGRESS_IP=$2

if [[ -z "$TENANT" ]]; then
    echo "Usage: $0 <tenant-name> <ingress-ip>" 1>&2
    exit 1
fi

if [[ -z "$INGRESS_IP" ]]; then
    echo "Usage: $0 <tenant-name> <ingress-ip>" 1>&2
    echo "To get ingress IP: kubectl get ingress apps-ingress-${TENANT} -n ${TENANT} -o jsonpath='{.status.loadBalancer.ingress[0].ip}'"
    exit 1
fi

echo "Creating DNS records for tenant ${TENANT} with IP ${INGRESS_IP}..."

# Add main tenant domain
az network dns record-set a add-record \
    --resource-group ${RESOURCE_GROUP} \
    --zone-name ${DNS_ZONE} \
    --record-set-name ${TENANT} \
    --ipv4-address $INGRESS_IP

# Add Keycloak subdomain
az network dns record-set a add-record \
    --resource-group ${RESOURCE_GROUP} \
    --zone-name ${DNS_ZONE} \
    --record-set-name keycloak.${TENANT} \
    --ipv4-address $INGRESS_IP

# Add Agent Proxy subdomain
az network dns record-set a add-record \
    --resource-group ${RESOURCE_GROUP} \
    --zone-name ${DNS_ZONE} \
    --record-set-name agentproxy.${TENANT} \
    --ipv4-address $INGRESS_IP

# Add RDP Proxy subdomain
az network dns record-set a add-record \
    --resource-group ${RESOURCE_GROUP} \
    --zone-name ${DNS_ZONE} \
    --record-set-name rdpproxy.${TENANT} \
    --ipv4-address $INGRESS_IP

echo "✅ DNS records created successfully!"
echo "   ${TENANT}.${DNS_ZONE} → ${INGRESS_IP}"
echo "   keycloak.${TENANT}.${DNS_ZONE} → ${INGRESS_IP}"
echo "   agentproxy.${TENANT}.${DNS_ZONE} → ${INGRESS_IP}"
echo "   rdpproxy.${TENANT}.${DNS_ZONE} → ${INGRESS_IP}"
