# Sentrius GKE Deployment Scripts

This directory contains scripts for deploying Sentrius to Google Kubernetes Engine (GKE).

## Prerequisites

1. **Google Cloud SDK** installed and configured
   ```bash
   gcloud auth login
   gcloud config set project sentrius-project
   ```

2. **kubectl** configured to access your GKE cluster
   ```bash
   gcloud container clusters get-credentials sentrius-autopilot-cluster-1 --region us-east1
   ```

3. **Helm 3.x** installed

4. **Docker images** built and pushed to GCP Container Registry
   ```bash
   # From repository root, build and push all images
   cd /path/to/Sentrius-private
   ./ops-scripts/base/build-images.sh gcp --all
   ```

## Configuration

The deployment is configured through the following files:

- **`.gcp.env`** - Contains version numbers for all services
- **`ops-scripts/gcp/base.sh`** - Contains cluster and DNS zone configuration

### Environment Variables (.gcp.env)

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

### Cluster Configuration (base.sh)

```bash
NAMESPACE=sentrius
CLUSTER=sentrius-autopilot-cluster-1
REGION=us-east1
ZONE=sentrius-cloud
```

## Scripts

### deploy-helm.sh

Deploys Sentrius to GKE with all components.

**Usage:**
```bash
./ops-scripts/gcp/deploy-helm.sh --tenant <tenant-name> [--no-tls]
```

**Options:**
- `--tenant TENANT_NAME` - (Required) Name of the tenant to deploy
- `--no-tls` - (Optional) Disable TLS/SSL (not recommended for production)

**Example:**
```bash
# Deploy production tenant with TLS
./ops-scripts/gcp/deploy-helm.sh --tenant production

# Deploy test tenant without TLS
./ops-scripts/gcp/deploy-helm.sh --tenant test --no-tls
```

**What it does:**
1. Sources environment variables and secrets
2. Creates Kubernetes namespaces (`<tenant>` and `<tenant>-agents`)
3. Generates or retrieves secrets from Kubernetes
4. Deploys main Sentrius chart to `<tenant>` namespace
5. Deploys launcher chart to `<tenant>-agents` namespace
6. Waits for LoadBalancer IP to be assigned
7. Creates DNS records for:
   - `<tenant>.sentrius.cloud` (main application)
   - `keycloak.<tenant>.sentrius.cloud` (authentication)
   - `agentproxy.<tenant>.sentrius.cloud` (agent proxy)
   - `rdpproxy.<tenant>.sentrius.cloud` (RDP proxy)

### test-helm.sh

Tests Helm chart rendering without deploying.

**Usage:**
```bash
./ops-scripts/gcp/test-helm.sh [tenant-name]
```

**Example:**
```bash
./ops-scripts/gcp/test-helm.sh my-tenant
```

### restart.sh

Restarts all deployments in the default namespace and upgrades the Helm release.

**Usage:**
```bash
./ops-scripts/gcp/restart.sh
```

### destroy-tenant.sh

Completely removes a tenant deployment including namespaces and DNS records.

**Usage:**
```bash
./ops-scripts/gcp/destroy-tenant.sh <tenant-name>
```

**Example:**
```bash
./ops-scripts/gcp/destroy-tenant.sh test-tenant
```

**Warning:** This is destructive and cannot be undone. It will:
1. Uninstall Helm releases
2. Delete Kubernetes namespaces (`<tenant>` and `<tenant>-agents`)
3. Remove all DNS records

### create-subdomain.sh

Manually creates DNS records for a tenant (useful if deploy-helm.sh DNS creation fails).

**Usage:**
```bash
./ops-scripts/gcp/create-subdomain.sh <tenant-name> <ingress-ip>
```

**Example:**
```bash
# Get the ingress IP first
INGRESS_IP=$(kubectl get ingress managed-cert-ingress-production -n production -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

# Create DNS records
./ops-scripts/gcp/create-subdomain.sh production $INGRESS_IP
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
./ops-scripts/gcp/remove-subdomain.sh <tenant-name>
```

**Example:**
```bash
./ops-scripts/gcp/remove-subdomain.sh old-tenant
```

**Note:** This is also called automatically by `destroy-tenant.sh`.

### spindown.sh

Scales down the GKE cluster to zero nodes.

**Usage:**
```bash
./ops-scripts/gcp/spindown.sh
```

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

### DNS Configuration

The deployment automatically creates DNS records in the `sentrius-cloud` zone:

- `<tenant>.sentrius.cloud` → Main application
- `keycloak.<tenant>.sentrius.cloud` → Keycloak authentication
- `agentproxy.<tenant>.sentrius.cloud` → Agent proxy service
- `rdpproxy.<tenant>.sentrius.cloud` → RDP proxy service

All records point to the GKE Ingress LoadBalancer IP.

## Secret Management

Secrets are automatically generated and stored in Kubernetes secrets:

- **`<tenant>-keycloak-secrets`** - Keycloak database and client secrets
- **`<tenant>-db-secret`** - Application database credentials
- **`<tenant>-oauth2-secrets`** - OAuth2 client secrets for services

Secrets are persisted across deployments. On first deployment, new secrets are generated. On subsequent deployments, existing secrets are reused.

## Building and Pushing Images

To build and push all images to GCP Container Registry:

```bash
# Build all images for GCP
./ops-scripts/base/build-images.sh gcp --all

# Build specific images
./ops-scripts/base/build-images.sh gcp --sentrius
./ops-scripts/base/build-images.sh gcp --sentrius-keycloak
./ops-scripts/base/build-images.sh gcp --sentrius-launcher-service

# Build with no cache (clean build)
./ops-scripts/base/build-images.sh gcp --all --no-cache
```

The build script automatically:
1. Increments patch version in `.gcp.env`
2. Builds Docker images
3. Tags images with version number
4. Pushes to `us-central1-docker.pkg.dev/sentrius-project/sentrius-repo`

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
kubectl get ingress managed-cert-ingress-<tenant> -n <tenant>

# Manually create DNS records
./ops-scripts/gcp/create-subdomain.sh <tenant>
```

### Secret Issues

If secrets are corrupted or need to be regenerated:

```bash
# Delete existing secrets
kubectl delete secret <tenant>-keycloak-secrets -n <tenant>
kubectl delete secret <tenant>-db-secret -n <tenant>

# Redeploy (new secrets will be generated)
./ops-scripts/gcp/deploy-helm.sh --tenant <tenant>
```

### Image Pull Issues

Ensure images are pushed to the registry:

```bash
# List images in registry
gcloud container images list --repository=us-central1-docker.pkg.dev/sentrius-project/sentrius-repo

# Check specific image tags
gcloud container images list-tags us-central1-docker.pkg.dev/sentrius-project/sentrius-repo/sentrius
```

## Upgrading Deployments

To upgrade an existing deployment:

1. Update version numbers in `.gcp.env`
2. Build and push new images
3. Redeploy using `deploy-helm.sh`

```bash
# Edit .gcp.env to update versions
vim .gcp.env

# Build and push updated images
./ops-scripts/base/build-images.sh gcp --all

# Upgrade deployment
./ops-scripts/gcp/deploy-helm.sh --tenant <tenant>
```

## Cost Optimization

To reduce costs when not in use:

```bash
# Scale down cluster to zero nodes
./ops-scripts/gcp/spindown.sh

# Or delete specific tenant
./ops-scripts/gcp/destroy-tenant.sh <tenant>
```

## Security Considerations

1. **TLS/SSL**: Always use TLS in production (default behavior)
2. **Secrets**: Secrets are auto-generated and stored in Kubernetes
3. **DNS**: Uses Google Cloud DNS for managed DNS records
4. **Network**: Services are exposed via GKE Ingress with LoadBalancer
5. **Authentication**: Keycloak provides OAuth2/OIDC authentication

## Support

For issues or questions:
1. Check logs: `kubectl logs -n <tenant> <pod-name>`
2. Review Helm values: `helm get values sentrius -n <tenant>`
3. Test chart rendering: `./ops-scripts/gcp/test-helm.sh <tenant>`
