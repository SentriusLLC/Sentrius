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
# This keeps:
# ✅ Configurations, secrets, ingresses
# ✅ Load balancers and IPs (so DNS stays valid)
# ✅ Certificates (already provisioned)
# ❌ Stops: All pods/containers (costs ~$0)

# To restart:
kubectl scale deployment --all --replicas=1 -n december
kubectl scale deployment --all --replicas=1 -n december-agents
kubectl scale statefulset --all --replicas=1 -n december