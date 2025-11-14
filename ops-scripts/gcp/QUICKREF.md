# GKE Deployment Quick Reference

## Prerequisites Setup
```bash
# Authenticate with GCP
gcloud auth login
gcloud config set project sentrius-project

# Configure kubectl for GKE cluster
gcloud container clusters get-credentials sentrius-autopilot-cluster-1 --region us-east1

# Verify cluster access
kubectl cluster-info
```

## Building and Pushing Images

### Build All Images for GCP
```bash
# Build all images and push to GCP registry
./ops-scripts/base/build-images.sh gcp --all

# Build with no cache (clean build)
./ops-scripts/base/build-images.sh gcp --all --no-cache
```

### Build Specific Images
```bash
./ops-scripts/base/build-images.sh gcp --sentrius
./ops-scripts/base/build-images.sh gcp --sentrius-keycloak
./ops-scripts/base/build-images.sh gcp --sentrius-launcher-service
./ops-scripts/base/build-images.sh gcp --sentrius-agent-proxy
./ops-scripts/base/build-images.sh gcp --sentrius-ssh-proxy
./ops-scripts/base/build-images.sh gcp --sentrius-rdp-proxy
```

## Deployment

### Deploy New Tenant
```bash
# Deploy with TLS (recommended)
./ops-scripts/gcp/deploy-helm.sh --tenant production

# Deploy without TLS (testing only)
./ops-scripts/gcp/deploy-helm.sh --tenant test --no-tls
```

### Access Points After Deployment
```
https://<tenant>.sentrius.cloud              - Main application
https://keycloak.<tenant>.sentrius.cloud      - Keycloak authentication
https://agentproxy.<tenant>.sentrius.cloud    - Agent proxy
https://rdpproxy.<tenant>.sentrius.cloud      - RDP proxy
```

## Monitoring and Troubleshooting

### Check Deployment Status
```bash
# Check main namespace
kubectl get deployments -n <tenant>
kubectl get pods -n <tenant>
kubectl get services -n <tenant>
kubectl get ingress -n <tenant>

# Check launcher namespace
kubectl get deployments -n <tenant>-agents
kubectl get pods -n <tenant>-agents
```

### View Logs
```bash
# Main API logs
kubectl logs -n <tenant> deployment/sentrius-sentrius -f

# Keycloak logs
kubectl logs -n <tenant> deployment/sentrius-keycloak -f

# Launcher logs
kubectl logs -n <tenant>-agents deployment/<tenant>-agents-launcherservice -f

# Get logs from specific pod
kubectl logs -n <tenant> <pod-name> -f
```

### Check Ingress and DNS
```bash
# Get LoadBalancer IP
kubectl get ingress managed-cert-ingress-<tenant> -n <tenant>

# List DNS records for tenant
gcloud dns record-sets list --zone=sentrius-cloud | grep <tenant>
```

### Check Secrets
```bash
# List secrets
kubectl get secrets -n <tenant>

# View secret content (base64 encoded)
kubectl get secret <tenant>-keycloak-secrets -n <tenant> -o yaml

# Decode specific secret value
kubectl get secret <tenant>-keycloak-secrets -n <tenant> -o jsonpath='{.data.db-password}' | base64 --decode
```

## Updating Deployments

### Update Image Versions
```bash
# 1. Edit .gcp.env to update version numbers
vim .gcp.env

# 2. Build and push new images
./ops-scripts/base/build-images.sh gcp --all

# 3. Redeploy (automatically uses new versions)
./ops-scripts/gcp/deploy-helm.sh --tenant <tenant>
```

### Restart Existing Deployment
```bash
# For default namespace
./ops-scripts/gcp/restart.sh

# For specific deployment
kubectl rollout restart deployment/<deployment-name> -n <tenant>
```

## DNS Management

### Manual DNS Record Creation
```bash
# Get ingress IP
INGRESS_IP=$(kubectl get ingress managed-cert-ingress-<tenant> -n <tenant> -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Create DNS records
./ops-scripts/gcp/create-subdomain.sh <tenant> $INGRESS_IP
```

### Remove DNS Records
```bash
./ops-scripts/gcp/remove-subdomain.sh <tenant>
```

## Cleanup

### Delete Specific Tenant
```bash
# Warning: This is destructive!
./ops-scripts/gcp/destroy-tenant.sh <tenant>
```

### Scale Down Cluster
```bash
# Reduce to zero nodes (stops billing for compute)
./ops-scripts/gcp/spindown.sh
```

## Validation

### Test Helm Chart Rendering
```bash
# Test without actually deploying
./ops-scripts/gcp/test-helm.sh <tenant>
```

### Lint Helm Charts
```bash
helm lint sentrius-chart
helm lint sentrius-chart-launcher
```

## Common Issues

### Image Pull Errors
```bash
# Verify images exist in registry
gcloud container images list --repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo

# Check specific image tags
gcloud container images list-tags us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius
```

### LoadBalancer IP Not Assigned
```bash
# Check ingress status
kubectl describe ingress managed-cert-ingress-<tenant> -n <tenant>

# Check for any events or errors
kubectl get events -n <tenant> --sort-by='.lastTimestamp'
```

### Secret Issues
```bash
# Delete corrupted secrets
kubectl delete secret <tenant>-keycloak-secrets -n <tenant>
kubectl delete secret <tenant>-db-secret -n <tenant>

# Redeploy (new secrets will be generated)
./ops-scripts/gcp/deploy-helm.sh --tenant <tenant>
```

### DNS Propagation
```bash
# Check if DNS records exist
gcloud dns record-sets list --zone=sentrius-cloud | grep <tenant>

# Test DNS resolution
nslookup <tenant>.sentrius.cloud
dig <tenant>.sentrius.cloud
```

## Production Checklist

- [ ] All images built and pushed to GCP registry
- [ ] .gcp.env has correct version numbers
- [ ] GKE cluster is running and accessible
- [ ] DNS zone configured correctly in base.sh
- [ ] Deploy with --tenant flag (do not use --no-tls)
- [ ] Verify LoadBalancer IP is assigned
- [ ] Verify DNS records are created
- [ ] Check all pods are running
- [ ] Test access to all subdomains
- [ ] Verify Keycloak authentication works
- [ ] Check application logs for errors

## Environment Files

### .gcp.env (Version Numbers)
```bash
SENTRIUS_VERSION=1.0.48
SENTRIUS_SSH_VERSION=1.0.7
SENTRIUS_KEYCLOAK_VERSION=1.0.10
SENTRIUS_AGENT_VERSION=1.0.19
SENTRIUS_AI_AGENT_VERSION=1.0.0
LLMPROXY_VERSION=1.0.0
LAUNCHER_VERSION=1.0.0
AGENTPROXY_VERSION=1.0.0
SSHPROXY_VERSION=1.0.0
RDPPROXY_VERSION=1.0.0
GITHUB_MCP_VERSION=1.0.0
```

### .generated.env (Auto-Generated Secrets)
Created automatically by generate-secrets.sh, contains:
- KEYCLOAK_DB_PASSWORD
- KEYCLOAK_CLIENT_SECRET
- KEYCLOAK_ADMIN_PASSWORD
- DB_PASSWORD
- KEYSTORE_PASSWORD
- Various OAuth2 client secrets

## Support

For detailed documentation, see: `ops-scripts/gcp/README.md`
