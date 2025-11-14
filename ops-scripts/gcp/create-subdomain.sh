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
    echo "To get ingress IP: kubectl get ingress managed-cert-ingress-${TENANT} -n ${TENANT} -o jsonpath='{.status.loadBalancer.ingress[0].ip}'"
    exit 1
fi

echo "Creating DNS records for tenant ${TENANT} with IP ${INGRESS_IP}..."

# Start DNS transaction
gcloud dns record-sets transaction start --zone=${ZONE}

# Add main tenant domain
gcloud dns record-sets transaction add --zone=${ZONE} \
  --name=${TENANT}.sentrius.cloud. \
  --type=A \
  --ttl=300 \
  $INGRESS_IP

# Add Keycloak subdomain
gcloud dns record-sets transaction add --zone=${ZONE} \
  --name=keycloak.${TENANT}.sentrius.cloud. \
  --type=A \
  --ttl=300 \
  $INGRESS_IP

# Add Agent Proxy subdomain
gcloud dns record-sets transaction add --zone=${ZONE} \
  --name=agentproxy.${TENANT}.sentrius.cloud. \
  --type=A \
  --ttl=300 \
  $INGRESS_IP

# Add RDP Proxy subdomain
gcloud dns record-sets transaction add --zone=${ZONE} \
  --name=rdpproxy.${TENANT}.sentrius.cloud. \
  --type=A \
  --ttl=300 \
  $INGRESS_IP

# Execute transaction
gcloud dns record-sets transaction execute --zone=${ZONE}

echo "✅ DNS records created successfully!"
echo "   ${TENANT}.sentrius.cloud → ${INGRESS_IP}"
echo "   keycloak.${TENANT}.sentrius.cloud → ${INGRESS_IP}"
echo "   agentproxy.${TENANT}.sentrius.cloud → ${INGRESS_IP}"
echo "   rdpproxy.${TENANT}.sentrius.cloud → ${INGRESS_IP}"
