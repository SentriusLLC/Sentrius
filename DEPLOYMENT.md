# Deployment Guide

This guide covers deployment options for Sentrius across different environments.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Local Development](#local-development)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Cloud Deployments](#cloud-deployments)
- [Configuration](#configuration)

## Prerequisites

### Required
- **Java 17** or later
- **Apache Maven 3.6+**
- **PostgreSQL** database for storing session and configuration data
- **Keycloak** for user authentication and authorization
- **OpenTelemetry** endpoint for observability

### Optional
- **Docker & Kubernetes** for containerized deployments
- **Neo4j** for graph-based analysis
- **Kafka** for event streaming
- **Python 3.12+** for Python agents

## Local Development

### Quick Start with Script

For convenience, use the `run-sentrius.sh` script which starts the core and API modules:

```bash
# Build the project first
mvn clean install

# Run Sentrius locally (requires PostgreSQL and Keycloak)
./ops-scripts/local/run-sentrius.sh --build
```

### Manual Start

```bash
# Build the project
mvn clean install

# Start the API server
cd api
mvn spring-boot:run
```

### Environment Variables

Configure using environment variables:

```bash
export KEYCLOAK_BASE_URL=http://localhost:8180
export DATABASE_PASSWORD=password
export KEYSTORE_PASSWORD=keystorepassword
cd api
mvn spring-boot:run
```

## Kubernetes Deployment

### Build Docker Images

#### Local Kubernetes
Build all images sequentially:
```bash
./ops-scripts/base/build-images.sh --all --no-cache
```

Or build concurrently for faster builds (recommended):
```bash
./ops-scripts/base/build-all-images-concurrent.sh --all --no-cache
```

#### GCP Container Registry
```bash
# Build and push to GCP Container Registry
./ops-scripts/base/build-images.sh gcp --all
```

#### Azure Container Registry
```bash
# Login to Azure Container Registry
az acr login --name sentriusacr

# Build and push to Azure Container Registry
./ops-scripts/base/build-images.sh azure --all
```

### Local Kubernetes Deployment

#### HTTP Deployment (Recommended for Development)

```bash
# Deploy to local Kubernetes cluster
./ops-scripts/local/deploy-helm.sh

# Forward ports for local access
kubectl port-forward -n dev service/sentrius-sentrius 8080:8080
kubectl port-forward -n dev service/sentrius-keycloak 8081:8081
```

Add to `/etc/hosts`:
```
127.0.0.1 sentrius-sentrius
127.0.0.1 sentrius-keycloak
```

Access at:
- Sentrius UI: http://localhost:8080
- Keycloak: http://localhost:8081

#### TLS Deployment

```bash
# Deploy with TLS and auto-install cert-manager
./ops-scripts/local/deploy-helm.sh --tls --install-cert-manager
```

Add to `/etc/hosts`:
```
127.0.0.1 sentrius-dev.local
127.0.0.1 keycloak-dev.local
```

Access at:
- Sentrius UI: https://sentrius-dev.local
- Keycloak: https://keycloak-dev.local

**Note**: Self-signed certificates will be automatically generated.

## Cloud Deployments

### GCP/GKE Deployment

```bash
# Deploy to GKE cluster
./ops-scripts/gcp/deploy-helm.sh --tenant <tenant-name>
```

**Note**: Ensure you're connected to your GKE cluster and have the necessary permissions.

For detailed GCP deployment documentation, see [ops-scripts/gcp/README.md](ops-scripts/gcp/README.md).

### Azure/AKS Deployment

```bash
# Deploy to AKS cluster (default domain: trustpolicy.ai)
./ops-scripts/azure/deploy-helm.sh --tenant <tenant-name>

# Deploy with custom domain
./ops-scripts/azure/deploy-helm.sh --tenant <tenant-name> --domain mycompany.com
```

**Prerequisites:**
- Azure CLI configured: `az login && az aks get-credentials --resource-group sentrius-rg --name sentrius-aks-cluster`
- Docker images pushed to Azure Container Registry
- DNS zone configured in Azure DNS (default: trustpolicy.ai)

**Default Domain**: Azure deployments use `trustpolicy.ai` by default. You can specify a custom domain with the `--domain` parameter.

For detailed Azure deployment documentation, see [ops-scripts/azure/README.md](ops-scripts/azure/README.md).

### AWS Deployment

Sentrius Helm charts support AWS EKS deployments. See [Helm Chart Configuration](#helm-chart-configuration) for environment-specific settings.

## Helm Chart Configuration

### Available Charts

1. **sentrius-chart** - Complete Sentrius deployment with all services
2. **sentrius-chart-launcher** - Lightweight chart focused on the launcher service

### Key Configuration Options

#### Environment Settings

```yaml
environment: "local"  # Options: local, gke, aws, azure
tenant: "my-company"
subdomain: "my-company.sentrius.cloud"
```

#### Core Services

```yaml
sentrius:
  image:
    repository: sentrius
    tag: latest

llmproxy:
  image:
    repository: sentrius-llmproxy
    tag: latest

postgres:
  storageSize: "10Gi"
```

#### Ingress Configuration

```yaml
ingress:
  enabled: true
  class: "nginx"  # or "gce" for GKE, "alb" for AWS
  tlsEnabled: true
  annotations: {}
```

#### TLS/SSL Configuration

For production with Let's Encrypt:
```yaml
certificates:
  enabled: true
  issuer: "letsencrypt-prod"

ingress:
  tlsEnabled: true
```

For local development with self-signed certificates:
```yaml
environment: local
certificates:
  enabled: true
ingress:
  tlsEnabled: true
```

### Custom Values Example

Create a `my-values.yaml` file:

```yaml
environment: "gke"
tenant: "my-company"
subdomain: "my-company.sentrius.cloud"

sentrius:
  image:
    repository: "my-registry/sentrius"
    tag: "v1.0.0"

postgres:
  storageSize: "20Gi"

ingress:
  enabled: true
  tlsEnabled: true
  class: "gce"
```

Deploy with custom values:
```bash
helm install my-sentrius sentrius-chart -f my-values.yaml
```

### Multi-Environment Support

The charts support multiple deployment environments with different configurations:

**Local Development:**
- Uses NodePort services
- Minimal resource requirements
- In-memory storage options

**GKE (Google Cloud):**
- Uses LoadBalancer services
- Managed certificates
- Persistent storage

**AWS:**
- ALB ingress support
- EBS storage classes
- AWS-specific annotations

**Azure:**
- Azure Load Balancer integration
- Azure disk storage
- Azure-specific networking

## Configuration

### Database Configuration

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/sentrius
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
```

### Keycloak Authentication

```properties
keycloak.realm=sentrius
keycloak.base-url=${KEYCLOAK_BASE_URL:http://localhost:8180}
spring.security.oauth2.client.registration.keycloak.client-secret=${KEYCLOAK_SECRET:defaultSecret}
spring.security.oauth2.client.registration.keycloak.client-id=sentrius-api
spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.keycloak.redirect-uri=${BASE_URL:http://localhost:8080}/login/oauth2/code/keycloak
spring.security.oauth2.client.registration.keycloak.scope=openid,profile,email
spring.security.oauth2.resourceserver.jwt.issuer-uri=${KEYCLOAK_BASE_URL:http://localhost:8180}/realms/sentrius
spring.security.oauth2.client.provider.keycloak.issuer-uri=${KEYCLOAK_BASE_URL:http://localhost:8180}/realms/sentrius
```

### SSH Settings

```properties
sentrius.ssh.port=22
sentrius.ssh.connection-timeout=30000
```

## Testing Deployments

### Helm Chart Testing

Test Helm charts locally before deployment:

```bash
# Test all charts
./ops-scripts/test-helm-charts.sh

# Test specific aspects
./ops-scripts/test-helm-charts.sh lint      # Lint charts
./ops-scripts/test-helm-charts.sh template  # Test rendering
./ops-scripts/test-helm-charts.sh config    # Test configurations
```

For detailed testing documentation, see [docs/helm-testing.md](docs/helm-testing.md).

## Troubleshooting

### Build Failures

```bash
# Clear Maven cache if build issues occur
rm -rf ~/.m2/repository
mvn clean install

# Check Java version
java -version  # Should be 17+
mvn -version   # Should be 3.6+
```

### Runtime Issues

```bash
# Check required services
curl http://localhost:8180  # Keycloak health
psql -h localhost -U postgres -d sentrius  # Database connectivity
```

### Container Issues

```bash
# Reset Docker environment for local development
eval $(minikube docker-env)
docker images | grep sentrius
```

## Next Steps

- Review [DEVELOPMENT.md](DEVELOPMENT.md) for development workflows
- See [INTEGRATIONS.md](INTEGRATIONS.md) for external service integrations
- Check [CUSTOM_AGENTS.md](CUSTOM_AGENTS.md) for creating custom agents
