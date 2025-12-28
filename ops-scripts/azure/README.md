# Sentrius Azure/AKS Deployment Scripts

This directory contains scripts for deploying Sentrius to Azure Kubernetes Service (AKS).

## Prerequisites

1. **Azure CLI** installed and configured
   ```bash
   az login
   az account set --subscription <subscription-id>
   ```

2. **kubectl** configured to access your AKS cluster
   ```bash
   az aks get-credentials --resource-group sentrius-rg --name sentrius-aks-cluster
   ```

3. **Helm 3.x** installed

4. **Docker images** built and pushed to Azure Container Registry
   ```bash
   # From repository root, build and push all images
   cd /path/to/Sentrius-private
   ./ops-scripts/base/build-images.sh azure --all
   ```

## Configuration

The deployment is configured through the following files:

- **`.azure.env`** - Contains version numbers for all services
- **`ops-scripts/azure/base.sh`** - Contains cluster, resource group, and DNS zone configuration

### Environment Variables (.azure.env)

```bash
SENTRIUS_VERSION=1.1.51
SENTRIUS_SSH_VERSION=1.1.10
SENTRIUS_KEYCLOAK_VERSION=1.1.13
SENTRIUS_AGENT_VERSION=1.1.22
SENTRIUS_AI_AGENT_VERSION=1.1.3
LLMPROXY_VERSION=1.1.3
LAUNCHER_VERSION=1.1.3
AGENTPROXY_VERSION=1.1.3
SSHPROXY_VERSION=1.1.3
RDPPROXY_VERSION=1.1.3
GITHUB_MCP_VERSION=1.1.3
MONITORING_AGENT_VERSION=1.1.21
SSH_AGENT_VERSION=1.1.3
```

### Cluster Configuration (base.sh)

```bash
NAMESPACE=august
CLUSTER=sentrius-aks-cluster
REGION=eastus
RESOURCE_GROUP=sentrius-rg
DNS_ZONE=trustpolicy.ai
```

### Azure Container Registry

Set the `AZURE_REGISTRY` environment variable to your Azure Container Registry:

```bash
export AZURE_REGISTRY=sentriusacr.azurecr.io
```

## Scripts

### deploy-helm.sh

Deploys Sentrius to AKS with all components.

**Usage:**
```bash
./ops-scripts/azure/deploy-helm.sh --tenant <tenant-name> [--no-tls]
```

**Options:**
- `--tenant TENANT_NAME` - (Required) Name of the tenant to deploy
- `--domain DOMAIN` - (Optional) Domain name for services (default: trustpolicy.ai)
- `--no-tls` - (Optional) Disable TLS/SSL (not recommended for production)

**Example:**
```bash
# Deploy production tenant with default domain (trustpolicy.ai)
./ops-scripts/azure/deploy-helm.sh --tenant production

# Deploy with custom domain
./ops-scripts/azure/deploy-helm.sh --tenant production --domain sentrius.cloud

# Deploy test tenant without TLS
./ops-scripts/azure/deploy-helm.sh --tenant test --no-tls
```

**What it does:**
1. Sources environment variables and secrets
2. Creates Kubernetes namespaces (`<tenant>` and `<tenant>-agents`)
3. Generates or retrieves secrets from Kubernetes
4. Deploys main Sentrius chart to `<tenant>` namespace
5. Deploys launcher chart to `<tenant>-agents` namespace
6. Waits for LoadBalancer IP to be assigned
7. Creates DNS records for:
   - `<tenant>.<domain>` (main application, default: trustpolicy.ai)
   - `keycloak.<tenant>.<domain>` (authentication)
   - `agentproxy.<tenant>.<domain>` (agent proxy)
   - `rdpproxy.<tenant>.<domain>` (RDP proxy)

**Note:** The default domain is `trustpolicy.ai`. You can specify a custom domain with `--domain`.

### spinup.sh

Scales up all deployments to 1 replica (for resuming after spindown).

**Usage:**
```bash
./ops-scripts/azure/spinup.sh --tenant <tenant-name>
```

**Example:**
```bash
./ops-scripts/azure/spinup.sh --tenant production
```

### spindown.sh

Scales down all deployments to 0 replicas to save costs while preserving configuration.

**Usage:**
```bash
./ops-scripts/azure/spindown.sh --tenant <tenant-name>
```

**Example:**
```bash
./ops-scripts/azure/spindown.sh --tenant test
```

**What it preserves:**
- Configurations and secrets
- Ingresses and load balancers
- DNS records
- Certificates

### restart.sh

Restarts all deployments in the default namespace and upgrades the Helm release.

**Usage:**
```bash
./ops-scripts/azure/restart.sh
```

### shutdown.sh

Completely removes a tenant deployment including namespaces and DNS records.

**Usage:**
```bash
./ops-scripts/azure/shutdown.sh --tenant <tenant-name>
```

**Example:**
```bash
./ops-scripts/azure/shutdown.sh --tenant test-tenant
```

**Warning:** This is destructive and cannot be undone. It will:
1. Uninstall Helm releases
2. Delete Kubernetes namespaces (`<tenant>` and `<tenant>-agents`)
3. Remove all DNS records

### destroy-tenant.sh

Alternative name for shutdown.sh - completely removes a tenant deployment.

**Usage:**
```bash
./ops-scripts/azure/destroy-tenant.sh <tenant-name>
```

**Example:**
```bash
./ops-scripts/azure/destroy-tenant.sh old-tenant
```

### test-helm.sh

Tests Helm chart rendering without deploying.

**Usage:**
```bash
./ops-scripts/azure/test-helm.sh [tenant-name]
```

**Example:**
```bash
./ops-scripts/azure/test-helm.sh my-tenant
```

### create-subdomain.sh

Manually creates DNS records for a tenant (useful if deploy-helm.sh DNS creation fails).

**Usage:**
```bash
./ops-scripts/azure/create-subdomain.sh <tenant-name> <ingress-ip>
```

**Example:**
```bash
# Get the ingress IP first
INGRESS_IP=$(kubectl get ingress apps-ingress-production -n production -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Create DNS records
./ops-scripts/azure/create-subdomain.sh production $INGRESS_IP
```

**What it creates:**
- `<tenant>.sentrius.cloud` → Ingress IP
- `keycloak.<tenant>.sentrius.cloud` → Ingress IP
- `agentproxy.<tenant>.sentrius.cloud` → Ingress IP
- `rdpproxy.<tenant>.sentrius.cloud` → Ingress IP

### remove-subdomain.sh

Removes DNS records for a tenant.

**Usage:**
```bash
./ops-scripts/azure/remove-subdomain.sh <tenant-name>
```

**Example:**
```bash
./ops-scripts/azure/remove-subdomain.sh old-tenant
```

**Note:** This is also called automatically by `destroy-tenant.sh` and `shutdown.sh`.

## Deployment Architecture

### Namespaces

Each tenant deployment creates two namespaces:

1. **`<tenant>`** - Main application namespace containing:
   - Sentrius API (`sentrius-sentrius`)
   - Keycloak (`sentrius-keycloak`)
   - PostgreSQL databases
   - Integration Proxy (`sentrius-integrationproxy`)
   - Agent Proxy (`sentrius-agentproxy`)
   - SSH/RDP Proxies
   - Neo4j, Kafka (optional)

2. **`<tenant>-agents`** - Agent launcher namespace containing:
   - Launcher Service (`sentrius-agents-launcherservice`)
   - Dynamic agent deployments

### Services Deployed

| Service | Image | Purpose |
|---------|-------|---------|
| Sentrius API | `sentrius` | Main application and REST API |
| Keycloak | `sentrius-keycloak` | Authentication and authorization |
| Integration Proxy | `sentrius-integration-proxy` | LLM and external service integration |
| Agent Proxy | `sentrius-agent-proxy` | Agent communication proxy |
| Launcher Service | `sentrius-launcher-service` | Dynamic agent lifecycle management |
| SSH Proxy | `sentrius-ssh-proxy` | SSH session proxy |
| RDP Proxy | `sentrius-rdp-proxy` | RDP session proxy |
| Java Agent | `sentrius-agent` | Java-based monitoring agent |
| AI Agent | `sentrius-ai-agent` | AI-powered monitoring agent |
| Monitoring Agent | `sentrius-monitoring-agent` | System monitoring agent |
| SSH Agent | `sentrius-ssh-agent` | SSH monitoring agent |

### DNS Configuration

The deployment automatically creates DNS records in Azure DNS:

- `<tenant>.<domain>` → Main application (default domain: trustpolicy.ai)
- `keycloak.<tenant>.<domain>` → Keycloak authentication
- `agentproxy.<tenant>.<domain>` → Agent proxy service
- `rdpproxy.<tenant>.<domain>` → RDP proxy service

All records point to the AKS Ingress LoadBalancer IP.

**Custom Domains**: To use a different domain, specify it with the `--domain` parameter:
```bash
./ops-scripts/azure/deploy-helm.sh --tenant production --domain mycompany.com
```

## Secret Management

Secrets are automatically generated and stored in Kubernetes secrets:

- **`<tenant>-keycloak-secrets`** - Keycloak database and client secrets
- **`<tenant>-db-secret`** - Application database credentials
- **`<tenant>-oauth2-secrets`** - OAuth2 client secrets for services

Secrets are persisted across deployments. On first deployment, new secrets are generated. On subsequent deployments, existing secrets are reused.

## Building and Pushing Images

To build and push all images to Azure Container Registry:

```bash
# Login to Azure Container Registry
az acr login --name sentriusacr

# Build all images for Azure
./ops-scripts/base/build-images.sh azure --all

# Build specific images
./ops-scripts/base/build-images.sh azure --sentrius
./ops-scripts/base/build-images.sh azure --sentrius-keycloak
./ops-scripts/base/build-images.sh azure --sentrius-launcher-service

# Build with no cache (clean build)
./ops-scripts/base/build-images.sh azure --all --no-cache
```

The build script automatically:
1. Increments patch version in `.azure.env`
2. Builds Docker images
3. Tags images with version number
4. Pushes to your Azure Container Registry

## Monitoring Deployment

After deployment, monitor the status:

```bash
# Check deployment status
kubectl get deployments -n <tenant>
kubectl get deployments -n <tenant>-agents

# Check pod status
kubectl get pods -n <tenant>
kubectl get pods -n <tenant>-agents

# Check services
kubectl get services -n <tenant>

# Check ingress
kubectl get ingress -n <tenant>

# View logs
kubectl logs -n <tenant> deployment/sentrius-sentrius
kubectl logs -n <tenant> deployment/sentrius-keycloak
```

## Troubleshooting

### DNS Records Not Created

If DNS records are not created automatically:

```bash
# Check if LoadBalancer IP is assigned
kubectl get ingress apps-ingress-<tenant> -n <tenant>

# Manually create DNS records
./ops-scripts/azure/create-subdomain.sh <tenant> <ingress-ip>
```

### Secret Issues

If secrets are corrupted or need to be regenerated:

```bash
# Delete existing secrets
kubectl delete secret <tenant>-keycloak-secrets -n <tenant>
kubectl delete secret <tenant>-db-secret -n <tenant>

# Redeploy (new secrets will be generated)
./ops-scripts/azure/deploy-helm.sh --tenant <tenant>
```

### Image Pull Issues

Ensure images are pushed to Azure Container Registry:

```bash
# List images in registry
az acr repository list --name sentriusacr --output table

# Check specific image tags
az acr repository show-tags --name sentriusacr --repository sentrius --output table
```

Ensure AKS has permission to pull from ACR:

```bash
# Grant AKS pull permissions to ACR
az aks update -n sentrius-aks-cluster -g sentrius-rg --attach-acr sentriusacr
```

### Ingress Controller Issues

Ensure Application Gateway Ingress Controller (AGIC) is installed:

```bash
# Check if AGIC is installed
kubectl get pods -n kube-system | grep ingress

# Install AGIC using Helm
helm repo add application-gateway-kubernetes-ingress https://appgwingress.blob.core.windows.net/ingress-azure-helm-package/
helm install ingress-azure application-gateway-kubernetes-ingress/ingress-azure
```

## Upgrading Deployments

To upgrade an existing deployment:

1. Update version numbers in `.azure.env`
2. Build and push new images
3. Redeploy using `deploy-helm.sh`

```bash
# Edit .azure.env to update versions
vim .azure.env

# Build and push updated images
./ops-scripts/base/build-images.sh azure --all

# Upgrade deployment
./ops-scripts/azure/deploy-helm.sh --tenant <tenant>
```

## Cost Optimization

To reduce costs when not in use:

```bash
# Scale down to zero replicas (preserves configuration)
./ops-scripts/azure/spindown.sh --tenant <tenant>

# Scale back up when needed
./ops-scripts/azure/spinup.sh --tenant <tenant>

# Or delete specific tenant completely
./ops-scripts/azure/destroy-tenant.sh <tenant>
```

## Security Considerations

1. **TLS/SSL**: Always use TLS in production (default behavior)
2. **Secrets**: Secrets are auto-generated and stored in Kubernetes
3. **DNS**: Uses Azure DNS for managed DNS records
4. **Network**: Services are exposed via AKS Ingress with LoadBalancer
5. **Authentication**: Keycloak provides OAuth2/OIDC authentication
6. **Container Registry**: Use Azure Container Registry with managed identities

## Azure-Specific Configuration

### Storage Classes

AKS provides several storage classes:

- `managed-premium` - Premium SSD (default for Sentrius)
- `managed-standard` - Standard HDD
- `azurefile` - Azure Files (for ReadWriteMany)

Configure in Helm values:
```yaml
config:
  storageClassName: "managed-premium"
```

### Ingress Classes

For AKS, use Application Gateway Ingress Controller:

```yaml
ingress:
  class: "azure-application-gateway"
```

### Load Balancer Annotations

Azure-specific annotations are automatically applied:

```yaml
service.beta.kubernetes.io/azure-load-balancer-resource-group: sentrius-rg
```

## Support

For issues or questions:
1. Check logs: `kubectl logs -n <tenant> <pod-name>`
2. Review Helm values: `helm get values sentrius -n <tenant>`
3. Test chart rendering: `./ops-scripts/azure/test-helm.sh <tenant>`
4. Check Azure resources: `az resource list --resource-group sentrius-rg`
