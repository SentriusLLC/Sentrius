#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh

TENANT="${1:-${NAMESPACE}}"

while [[ $# -gt 0 ]]; do
    case $1 in
        --tenant)
            TENANT="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 --tenant TENANT_NAME"
            echo "  --tenant: Specify tenant name (required)"
            exit 1
            ;;
    esac
done

echo "======================================"
echo "🗑️  Tearing Down Sentrius Deployment"
echo "======================================"

# Delete Helm releases
echo "📦 Uninstalling Helm releases..."
helm uninstall sentrius -n ${TENANT} 2>/dev/null || echo "  sentrius release not found"
helm uninstall sentrius-agents -n ${TENANT}-agents 2>/dev/null || echo "  sentrius-agents release not found"

# Delete ManagedCertificates explicitly (sometimes they linger)
echo "🔐 Deleting managed certificates..."
kubectl delete certificate --all -n ${TENANT} 2>/dev/null || true

# Delete Ingresses explicitly (to release load balancers)
echo "🌐 Deleting ingresses..."
kubectl delete ingress --all -n ${TENANT} 2>/dev/null || true

# Wait for load balancers to be removed
echo "⏳ Waiting for load balancers to be cleaned up..."
sleep 10

# Delete DNS records
echo "🌐 Deleting DNS records..."
for SUBDOMAIN in "keycloak.${TENANT}" "${TENANT}" "agentproxy.${TENANT}" "rdpproxy.${TENANT}"; do
    if az network dns record-set a show --resource-group ${RESOURCE_GROUP} --zone-name ${DNS_ZONE} --name ${SUBDOMAIN} 2>/dev/null | grep -q ${SUBDOMAIN}; then
        echo "  Deleting ${SUBDOMAIN}.${DNS_ZONE}..."
        az network dns record-set a delete \
            --resource-group ${RESOURCE_GROUP} \
            --zone-name ${DNS_ZONE} \
            --name ${SUBDOMAIN} \
            --yes 2>/dev/null || echo "  Failed to delete ${SUBDOMAIN}"
    fi
done

# Delete namespaces (this removes all remaining resources)
echo "📦 Deleting namespaces..."
kubectl delete namespace ${TENANT} --timeout=60s 2>/dev/null || echo "  Forcing namespace deletion..."
kubectl delete namespace ${TENANT}-agents --timeout=60s 2>/dev/null || echo "  Forcing namespace deletion..."

# If namespaces are stuck (sometimes happens with finalizers)
echo "🔍 Checking for stuck namespaces..."
if kubectl get namespace ${TENANT} >/dev/null 2>&1; then
    echo "  Namespace ${TENANT} is stuck, removing finalizers..."
    kubectl get namespace ${TENANT} -o json | \
        jq '.spec.finalizers = []' | \
        kubectl replace --raw /api/v1/namespaces/${TENANT}/finalize -f -
fi

if kubectl get namespace ${TENANT}-agents >/dev/null 2>&1; then
    echo "  Namespace ${TENANT}-agents is stuck, removing finalizers..."
    kubectl get namespace ${TENANT}-agents -o json | \
        jq '.spec.finalizers = []' | \
        kubectl replace --raw /api/v1/namespaces/${TENANT}-agents/finalize -f -
fi

echo ""
echo "======================================"
echo "✅ Teardown Complete!"
echo "======================================"
echo ""
echo "Verify cleanup with:"
echo "  kubectl get namespaces | grep ${TENANT}"
echo "  az network public-ip list --resource-group ${RESOURCE_GROUP}"
echo "  az network dns record-set a list --resource-group ${RESOURCE_GROUP} --zone-name ${DNS_ZONE}"
