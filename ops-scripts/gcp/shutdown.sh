#!/bin/bash

TENANT="december"
ZONE="sentrius-cloud"  # Your Cloud DNS zone name

while [[ $# -gt 0 ]]; do
    case $1 in
        --tenant)
            TENANT="$2"
            shift 2
            ;;
        --no-tls)
            CERTIFICATES_ENABLED="false"
            INGRESS_TLS_ENABLED="false"
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 --tenant TENANT_NAME [--no-tls]"
            echo "  --tenant: Specify tenant name (required)"
            echo "  --no-tls: Disable TLS/SSL (not recommended for production)"
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
kubectl delete managedcertificate --all -n ${TENANT} 2>/dev/null || true

# Delete Ingresses explicitly (to release load balancers)
echo "🌐 Deleting ingresses..."
kubectl delete ingress --all -n ${TENANT} 2>/dev/null || true

# Wait for load balancers to be removed
echo "⏳ Waiting for load balancers to be cleaned up..."
sleep 10

# Delete DNS records
echo "🌐 Deleting DNS records..."
for SUBDOMAIN in "keycloak.${TENANT}.sentrius.cloud" \
                 "${TENANT}.sentrius.cloud" \
                 "agentproxy.${TENANT}.sentrius.cloud" \
                 "rdpproxy.${TENANT}.sentrius.cloud"; do
    if gcloud dns record-sets list --zone=${ZONE} --filter="name:${SUBDOMAIN}." 2>/dev/null | grep -q ${SUBDOMAIN}; then
        echo "  Deleting ${SUBDOMAIN}..."
        gcloud dns record-sets delete ${SUBDOMAIN}. \
            --type=A \
            --zone=${ZONE} \
            --quiet 2>/dev/null || echo "  Failed to delete ${SUBDOMAIN}"
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
echo "  gcloud compute forwarding-rules list"
echo "  gcloud compute target-https-proxies list"
echo "  gcloud dns record-sets list --zone=${ZONE}"