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
echo "⚡ Starting Up Sentrius Deployment"
echo "======================================"

# Scale up all deployments to 1 replica
kubectl scale deployment --all --replicas=1 -n ${TENANT}
kubectl scale deployment --all --replicas=1 -n ${TENANT}-agents
kubectl scale statefulset --all --replicas=1 -n ${TENANT}

echo ""
echo "✅ Startup complete!"
echo ""
echo "Check status with:"
echo "  kubectl get pods -n ${TENANT}"
echo "  kubectl get pods -n ${TENANT}-agents"
