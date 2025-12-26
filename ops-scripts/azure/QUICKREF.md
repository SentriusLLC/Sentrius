# Azure/AKS Deployment Quick Reference

Quick reference guide for common Sentrius Azure/AKS deployment tasks.

## Initial Setup

```bash
# Login to Azure
az login
az account set --subscription <subscription-id>

# Configure kubectl for AKS
az aks get-credentials --resource-group sentrius-rg --name sentrius-aks-cluster

# Login to Azure Container Registry
az acr login --name sentriusacr

# Set Azure Container Registry environment variable
export AZURE_REGISTRY=sentriusacr.azurecr.io
```

## Common Commands

### Deploy New Tenant

```bash
# Deploy with TLS and default domain (trustpolicy.ai)
./ops-scripts/azure/deploy-helm.sh --tenant production

# Deploy with custom domain
./ops-scripts/azure/deploy-helm.sh --tenant production --domain mycompany.com

# Deploy without TLS (development only)
./ops-scripts/azure/deploy-helm.sh --tenant dev --no-tls
```

### Build and Push Images

```bash
# Build all images for Azure
./ops-scripts/base/build-images.sh azure --all

# Build specific image
./ops-scripts/base/build-images.sh azure --sentrius

# Build with no cache
./ops-scripts/base/build-images.sh azure --all --no-cache
```

### Start/Stop Deployments

```bash
# Stop all pods (saves costs, keeps config)
./ops-scripts/azure/spindown.sh --tenant production

# Start pods again
./ops-scripts/azure/spinup.sh --tenant production

# Restart with updated config
./ops-scripts/azure/restart.sh
```

### Completely Remove Tenant

```bash
# Remove everything (destructive!)
./ops-scripts/azure/shutdown.sh --tenant old-tenant
# OR
./ops-scripts/azure/destroy-tenant.sh old-tenant
```

### DNS Management

```bash
# Get ingress IP
INGRESS_IP=$(kubectl get ingress apps-ingress-production -n production -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Create DNS records manually
./ops-scripts/azure/create-subdomain.sh production $INGRESS_IP

# Remove DNS records
./ops-scripts/azure/remove-subdomain.sh production
```

### Testing

```bash
# Test Helm chart rendering
./ops-scripts/azure/test-helm.sh production

# Lint Helm charts
helm lint sentrius-chart
helm lint sentrius-chart-launcher
```

## Monitoring and Debugging

### Check Deployment Status

```bash
# Check all resources
kubectl get all -n production

# Check deployments
kubectl get deployments -n production
kubectl get deployments -n production-agents

# Check pods
kubectl get pods -n production
kubectl get pods -n production-agents

# Check ingress
kubectl get ingress -n production

# Check services
kubectl get services -n production
```

### View Logs

```bash
# Sentrius API logs
kubectl logs -n production deployment/sentrius-sentrius --tail=100 -f

# Keycloak logs
kubectl logs -n production deployment/sentrius-keycloak --tail=100 -f

# Agent logs
kubectl logs -n production-agents deployment/sentrius-agents-launcherservice --tail=100 -f

# All logs from a pod
kubectl logs -n production <pod-name> --all-containers=true
```

### Describe Resources

```bash
# Describe pod (shows events)
kubectl describe pod -n production <pod-name>

# Describe ingress
kubectl describe ingress -n production apps-ingress-production

# Describe deployment
kubectl describe deployment -n production sentrius-sentrius
```

### Execute Commands in Pods

```bash
# Get shell in pod
kubectl exec -it -n production <pod-name> -- /bin/bash

# Run single command
kubectl exec -n production <pod-name> -- ls -la /app
```

## Azure-Specific Commands

### Check AKS Cluster

```bash
# Get cluster info
az aks show --resource-group sentrius-rg --name sentrius-aks-cluster

# List node pools
az aks nodepool list --resource-group sentrius-rg --cluster-name sentrius-aks-cluster

# Scale node pool
az aks nodepool scale --resource-group sentrius-rg --cluster-name sentrius-aks-cluster --name default --node-count 3
```

### Check Container Registry

```bash
# List repositories
az acr repository list --name sentriusacr --output table

# List tags for image
az acr repository show-tags --name sentriusacr --repository sentrius --output table

# Delete old image
az acr repository delete --name sentriusacr --image sentrius:old-tag
```

### Check DNS Records

```bash
# List all DNS records
az network dns record-set a list --resource-group sentrius-rg --zone-name sentrius.cloud --output table

# Show specific record
az network dns record-set a show --resource-group sentrius-rg --zone-name sentrius.cloud --name production

# Delete DNS record
az network dns record-set a delete --resource-group sentrius-rg --zone-name sentrius.cloud --name old-tenant --yes
```

### Check Load Balancers

```bash
# List public IPs
az network public-ip list --resource-group sentrius-rg --output table

# Show load balancer
az network lb list --resource-group sentrius-rg --output table
```

## Troubleshooting

### Pods Not Starting

```bash
# Check pod status
kubectl get pods -n production

# View pod events
kubectl describe pod -n production <pod-name>

# Check logs
kubectl logs -n production <pod-name> --previous

# Check resource limits
kubectl top nodes
kubectl top pods -n production
```

### Image Pull Errors

```bash
# Check if ACR is attached to AKS
az aks show --resource-group sentrius-rg --name sentrius-aks-cluster --query "identity"

# Attach ACR to AKS
az aks update -n sentrius-aks-cluster -g sentrius-rg --attach-acr sentriusacr

# Verify image exists
az acr repository show --name sentriusacr --repository sentrius --image sentrius:1.1.51
```

### DNS Not Resolving

```bash
# Check DNS record exists
az network dns record-set a show --resource-group sentrius-rg --zone-name sentrius.cloud --name production

# Check ingress has IP
kubectl get ingress -n production

# Test DNS resolution
nslookup production.sentrius.cloud
dig production.sentrius.cloud
```

### Certificate Issues

```bash
# Check cert-manager
kubectl get pods -n cert-manager

# Check certificates
kubectl get certificate -n production

# Check certificate status
kubectl describe certificate -n production <cert-name>

# Check certificate secret
kubectl get secret -n production <cert-secret-name> -o yaml
```

### Ingress Not Working

```bash
# Check ingress controller
kubectl get pods -n kube-system | grep ingress

# Check ingress resource
kubectl describe ingress -n production apps-ingress-production

# Check Application Gateway
az network application-gateway show --resource-group sentrius-rg --name sentrius-appgw
```

## Version Management

### Update Versions

```bash
# Edit version file
vim .azure.env

# Update version number
SENTRIUS_VERSION=1.1.52
```

### Deploy New Version

```bash
# Build and push new images
./ops-scripts/base/build-images.sh azure --all

# Deploy updated version
./ops-scripts/azure/deploy-helm.sh --tenant production
```

### Rollback

```bash
# View Helm history
helm history sentrius -n production

# Rollback to previous version
helm rollback sentrius -n production

# Rollback to specific revision
helm rollback sentrius 3 -n production
```

## Secrets Management

### View Secrets

```bash
# List secrets
kubectl get secrets -n production

# View secret data (base64 encoded)
kubectl get secret production-keycloak-secrets -n production -o yaml

# Decode secret value
kubectl get secret production-keycloak-secrets -n production -o jsonpath="{.data.db-password}" | base64 --decode
```

### Regenerate Secrets

```bash
# Delete existing secret
kubectl delete secret production-keycloak-secrets -n production

# Redeploy (will generate new secret)
./ops-scripts/azure/deploy-helm.sh --tenant production
```

## Backup and Restore

### Backup Resources

```bash
# Backup namespace resources
kubectl get all -n production -o yaml > production-backup.yaml

# Backup secrets
kubectl get secrets -n production -o yaml > production-secrets-backup.yaml

# Backup configmaps
kubectl get configmaps -n production -o yaml > production-configmaps-backup.yaml
```

### Export Helm Values

```bash
# Get current Helm values
helm get values sentrius -n production > production-values.yaml

# Get all values including defaults
helm get values sentrius -n production --all > production-all-values.yaml
```

## Performance Optimization

### Scale Deployments

```bash
# Scale specific deployment
kubectl scale deployment sentrius-sentrius -n production --replicas=3

# Scale all deployments
kubectl scale deployment --all -n production --replicas=2
```

### Resource Monitoring

```bash
# Check node resources
kubectl top nodes

# Check pod resources
kubectl top pods -n production

# Check pod resource limits
kubectl describe pod -n production <pod-name> | grep -A 5 "Limits:"
```

## Quick Links

- **Main README**: [README.md](README.md)
- **Deployment Guide**: [../../DEPLOYMENT.md](../../DEPLOYMENT.md)
- **Helm Charts**: [../../sentrius-chart](../../sentrius-chart)
- **Azure Docs**: https://docs.microsoft.com/en-us/azure/aks/

## Environment Files

- **`.azure.env`** - Version numbers for all services
- **`base.sh`** - Cluster and DNS configuration
- **`.generated.env`** - Auto-generated secrets (not in git)

## Support

For issues:
1. Check logs: `kubectl logs -n <tenant> <pod-name>`
2. Check events: `kubectl get events -n <tenant> --sort-by='.lastTimestamp'`
3. Check Helm status: `helm status sentrius -n <tenant>`
4. Run test: `./ops-scripts/azure/test-helm.sh <tenant>`
