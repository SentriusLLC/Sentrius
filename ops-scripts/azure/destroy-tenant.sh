#!/bin/bash
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

source ${SCRIPT_DIR}/base.sh

TENANT=$1

if [[ -z "$TENANT" ]]; then
    echo "Usage: $0 <tenant-name>" 1>&2
    exit 1
fi

echo "======================================"
echo "🗑️  Destroying Tenant: ${TENANT}"
echo "======================================"

# Uninstall Helm releases
echo "📦 Uninstalling Helm releases..."
helm uninstall sentrius -n ${TENANT} 2>/dev/null || echo "  sentrius release not found"
helm uninstall sentrius-agents -n ${TENANT}-agents 2>/dev/null || echo "  sentrius-agents release not found"

# Delete ingresses to release load balancers
echo "🌐 Deleting ingresses..."
kubectl delete ingress --all -n ${TENANT} 2>/dev/null || true

# Wait for cleanup
echo "⏳ Waiting for resources to be cleaned up..."
sleep 10

# Remove DNS records
echo "🌐 Removing DNS records..."
${SCRIPT_DIR}/remove-subdomain.sh ${TENANT}

# Delete namespaces
echo "📦 Deleting namespaces..."
kubectl delete namespace ${TENANT} --timeout=60s 2>/dev/null || true
kubectl delete namespace ${TENANT}-agents --timeout=60s 2>/dev/null || true

# If namespaces are stuck
echo "🔍 Checking for stuck namespaces..."
if kubectl get namespace ${TENANT} >/dev/null 2>&1; then
    echo "  Removing finalizers from ${TENANT}..."
    kubectl get namespace ${TENANT} -o json | \
        jq '.spec.finalizers = []' | \
        kubectl replace --raw /api/v1/namespaces/${TENANT}/finalize -f -
fi

if kubectl get namespace ${TENANT}-agents >/dev/null 2>&1; then
    echo "  Removing finalizers from ${TENANT}-agents..."
    kubectl get namespace ${TENANT}-agents -o json | \
        jq '.spec.finalizers = []' | \
        kubectl replace --raw /api/v1/namespaces/${TENANT}-agents/finalize -f -
fi

echo ""
echo "======================================"
echo "✅ Tenant Destroyed!"
echo "======================================"
echo ""
echo "Verify cleanup:"
echo "  kubectl get namespaces | grep ${TENANT}"
echo "  az network dns record-set a list --resource-group ${RESOURCE_GROUP} --zone-name ${DNS_ZONE}"
