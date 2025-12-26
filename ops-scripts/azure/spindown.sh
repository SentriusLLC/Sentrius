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
echo "💤 Scaling Down Sentrius Deployment"
echo "======================================"

# This keeps:
# ✅ Configurations, secrets, ingresses
# ✅ Load balancers and IPs (so DNS stays valid)
# ✅ Certificates (already provisioned)
# ❌ Stops: All pods/containers (reduces costs)

# To restart:
# kubectl scale deployment --all --replicas=1 -n ${TENANT}
# kubectl scale deployment --all --replicas=1 -n ${TENANT}-agents
# kubectl scale statefulset --all --replicas=1 -n ${TENANT}

# Scale down all deployments to 0 replicas
kubectl scale deployment --all --replicas=0 -n ${TENANT}
kubectl scale deployment --all --replicas=0 -n ${TENANT}-agents
kubectl scale statefulset --all --replicas=0 -n ${TENANT}

echo ""
echo "✅ Spindown complete!"
echo ""
echo "To restart:"
echo "  kubectl scale deployment --all --replicas=1 -n ${TENANT}"
echo "  kubectl scale deployment --all --replicas=1 -n ${TENANT}-agents"
echo "  kubectl scale statefulset --all --replicas=1 -n ${TENANT}"
