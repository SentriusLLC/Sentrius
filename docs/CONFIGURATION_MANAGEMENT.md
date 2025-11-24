# Dynamic Configuration Management

## Overview

The Sentrius platform now uses **PersistentVolumeClaims (PVCs)** instead of ConfigMaps for configuration storage. This allows you to update configuration properties without requiring full stack redeployment or pod restarts.

## Architecture

### Previous Approach (ConfigMaps)
- Configuration stored in Kubernetes ConfigMaps
- Updates required pod restart (via rolling deployment)
- Full redeployment needed for changes to be visible

### New Approach (PVCs with Init Jobs)
- Configuration stored in ReadWriteMany PersistentVolumeClaims
- Initial configuration populated from ConfigMap via Helm hook job
- Updates visible immediately without pod restarts
- ConfigMap retained as template source for initial deployment

## Configuration Files

The following configuration files are managed via PVC:

### sentrius-chart
- `api-application.properties` - Main API server configuration
- `agentproxy-application.properties` - Agent proxy configuration
- `llmproxy-application.properties` - LLM proxy configuration
- `ai-agent-application.properties` - AI agent configuration
- `analysis-agent-application.properties` - Analysis agent configuration
- `monitoring-agent-application.properties` - Monitoring agent configuration
- `sshproxy-application.properties` - SSH proxy configuration
- `rdpproxy-application.properties` - RDP proxy configuration
- `dynamic.properties` - Dynamic runtime properties
- `default-policy.yaml` - Default agent policy
- `assessor-config.yaml` - Assessor agent configuration

### sentrius-chart-launcher
- `launcher.properties` - Launcher service configuration
- `agent.properties` - Agent configuration
- `chat-helper.yaml` - Chat helper agent configuration
- `assessor-config.yaml` - Assessor configuration
- `chat-atpl-helper.yaml` - ATPL helper configuration

## Deployment Workflow

### Initial Installation

1. Helm creates the PVC: `<release-name>-config-pvc`
2. Pre-install hook job runs: `<release-name>-config-init`
3. Init job copies all files from ConfigMap to PVC
4. Pods mount the PVC at `/config`
5. Applications load configuration from `/config`

### Updating Configuration

#### Option 1: Direct File Update (Recommended)

Update configuration files directly in the PVC without pod restart:

```bash
# Find a pod using the config PVC
POD_NAME=$(kubectl get pods -n <namespace> -l app=sentrius -o jsonpath='{.items[0].metadata.name}')

# Copy updated file to PVC
kubectl cp my-updated-config.properties <namespace>/${POD_NAME}:/config/api-application.properties

# Or edit directly in the pod
kubectl exec -it -n <namespace> ${POD_NAME} -- vi /config/api-application.properties
```

The changes will be immediately visible to:
- Running applications (for properties loaded dynamically)
- New application instances on restart
- All pods sharing the same PVC (ReadWriteMany)

#### Option 2: Update via Init Job (Full Reset)

To reset configuration to values in the ConfigMap:

```bash
# Update the ConfigMap in your values.yaml or via --set
helm upgrade <release-name> sentrius-chart --reuse-values

# The pre-upgrade hook will re-run the init job
# This overwrites ALL files in the PVC with ConfigMap data
```

## Dynamic Properties

The `dynamic.properties` file has special handling:

1. Loaded from `/config/dynamic.properties` at application startup
2. Values can be overridden at runtime via the database (ConfigurationOption table)
3. Database values take precedence over file values
4. Updates via UI/API are stored in database, not file

To update dynamic properties:
- **Runtime updates**: Use the Sentrius UI or API (stored in database)
- **Default values**: Update `/config/dynamic.properties` file in PVC

## Configuration Storage

### PVC vs ConfigMap Mode

The chart supports two modes for configuration storage:

1. **PVC Mode** (default for cloud environments): Uses PersistentVolumeClaim for dynamic updates
2. **ConfigMap Mode** (default for local): Uses ConfigMap directly (traditional approach)

```yaml
# Configuration options
config:
  usePVC: true  # Set to false for local dev or environments without ReadWriteMany storage
  storageSize: "1Gi"  # Size of PVC (only used when usePVC=true)
  storageClassName: ""  # Optional: Specify storage class supporting ReadWriteMany
  # Examples: "nfs", "azurefile", "efs.csi.aws.com", "filestore.csi.storage.gke.io"
```

### When to Use Each Mode

**Use PVC Mode (`usePVC: true`)** when:
- Deploying to cloud environments (GKE, AWS, Azure)
- You have ReadWriteMany storage available
- You want to update configs without pod restarts
- You need shared configuration across multiple pods

**Use ConfigMap Mode (`usePVC: false`)** when:
- Deploying to local development (minikube, kind, docker-desktop)
- Your cluster doesn't support ReadWriteMany storage
- You don't have NFS or equivalent network storage
- Traditional ConfigMap behavior is acceptable

### PVC Specifications

When using PVC mode, the PVC uses:
- **Access Mode**: `ReadWriteMany` - Allows multiple pods to read/write simultaneously
- **Storage Class**: Cluster default (or custom via `config.storageClassName`)
- **Reclaim Policy**: Depends on storage class (typically Retain or Delete)

**Important**: The storage class must support `ReadWriteMany` access mode. Common options:
- **GKE**: `filestore.csi.storage.gke.io` (Google Filestore)
- **AWS**: `efs.csi.aws.com` (Amazon EFS)
- **Azure**: `azurefile` (Azure Files)
- **On-Prem**: `nfs` or similar network storage

If your cluster's default storage class doesn't support ReadWriteMany, either:
1. Set `config.usePVC=false` to use ConfigMap mode
2. Specify a compatible storage class:

```bash
helm install sentrius sentrius-chart \
  --set config.usePVC=true \
  --set config.storageClassName=nfs-storage \
  --set tenant=my-tenant
```

### Local Development

For local development environments, the deployment script automatically sets `config.usePVC=false`:

```bash
# Local deployment uses ConfigMap mode by default
./ops-scripts/local/deploy-helm.sh
```

To force PVC mode in local (requires ReadWriteMany storage):
```bash
helm install sentrius sentrius-chart \
  --set config.usePVC=true \
  --set config.storageClassName=local-nfs \
  --set tenant=dev
```


### Storage Considerations

1. **Size**: 1Gi is sufficient for configuration files (actual usage ~10-50MB)
2. **Performance**: Configuration files are read infrequently, no high IOPS needed
3. **Backup**: Configuration is sourced from ConfigMap, can be recreated
4. **Persistence**: PVC persists across deployments (unless manually deleted)

## Troubleshooting

### Configuration Not Loading

```bash
# Check if PVC is bound
kubectl get pvc -n <namespace> | grep config-pvc

# Check if init job completed successfully
kubectl get jobs -n <namespace> | grep config-init

# Verify files exist in PVC
kubectl exec -it -n <namespace> <pod-name> -- ls -la /config/

# View specific config file
kubectl exec -it -n <namespace> <pod-name> -- cat /config/api-application.properties
```

### Init Job Fails

```bash
# View init job logs
kubectl logs -n <namespace> job/<release-name>-config-init

# Common issues:
# - PVC not bound: Check storage class and provisioner
# - ConfigMap missing: Verify ConfigMap exists
# - Permission issues: Check pod security policies
```

### Changes Not Visible

1. **For static properties**: Requires pod restart
   ```bash
   kubectl rollout restart deployment/<deployment-name> -n <namespace>
   ```

2. **For dynamic properties**: Check if property is in database
   ```sql
   SELECT * FROM configuration_option WHERE configuration_name = '<property-key>';
   ```

3. **Verify file was actually updated**:
   ```bash
   kubectl exec -it -n <namespace> <pod-name> -- cat /config/<config-file>
   ```

## Migration from ConfigMap

If upgrading from a ConfigMap-based deployment:

1. Existing ConfigMap values will be copied to PVC on first upgrade
2. Any manual changes to ConfigMap will be lost after upgrade
3. Future updates should be made directly to PVC files
4. ConfigMap remains as source template for init jobs

## Best Practices

1. **Version Control**: Keep configuration templates in git/ConfigMap
2. **Documentation**: Document any PVC-only changes separately
3. **Backup**: Periodically backup PVC contents to git
4. **Testing**: Test configuration changes in dev/staging before production
5. **Monitoring**: Monitor application logs after configuration changes

## Examples

### Update Database Connection Pool

```bash
# Get pod name
POD=$(kubectl get pods -n dev -l app=sentrius -o jsonpath='{.items[0].metadata.name}')

# Edit configuration
kubectl exec -it -n dev ${POD} -- sh -c "cat >> /config/api-application.properties << 'EOF'
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=10
EOF"

# Restart deployment to pick up changes
kubectl rollout restart deployment/sentrius-sentrius -n dev
```

### Bulk Configuration Update

```bash
# Prepare updated files locally
cat > api-application.properties << EOF
# Updated configuration
spring.datasource.hikari.maximum-pool-size=20
EOF

# Copy all files to PVC
for file in *.properties; do
  kubectl cp ${file} dev/${POD}:/config/${file}
done

# Verify files
kubectl exec -it -n dev ${POD} -- ls -la /config/
```

### Backup Current Configuration

```bash
# Create backup directory
mkdir -p config-backup/$(date +%Y%m%d)

# Copy all files from PVC
POD=$(kubectl get pods -n dev -l app=sentrius -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n dev ${POD} -- tar czf - /config | tar xzf - -C config-backup/$(date +%Y%m%d)
```

## Security Considerations

1. **Access Control**: Limit who can exec into pods or copy files
2. **Audit**: Log all configuration changes
3. **Encryption**: Consider encrypted storage class for sensitive configs
4. **Secrets**: Use Kubernetes Secrets for passwords, not config files
5. **RBAC**: Restrict PVC access via Kubernetes RBAC policies

## Related Resources

- [Kubernetes PersistentVolumeClaims](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)
- [Helm Hooks](https://helm.sh/docs/topics/charts_hooks/)
- [Spring Boot External Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
