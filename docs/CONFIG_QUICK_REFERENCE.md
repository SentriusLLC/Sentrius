# Quick Reference: Updating Sentrius Configuration

## Updating Configuration Without Redeployment

### Step 1: Find a Running Pod

```bash
# For main Sentrius services
kubectl get pods -n <namespace> -l app=sentrius

# For launcher service
kubectl get pods -n <tenant-namespace> -l app=launcherservice
```

### Step 2: Update Configuration File

**Option A: Copy from local file**
```bash
kubectl cp ./my-config.properties <namespace>/<pod-name>:/config/api-application.properties
```

**Option B: Edit directly**
```bash
kubectl exec -it -n <namespace> <pod-name> -- vi /config/api-application.properties
```

**Option C: Update specific property**
```bash
kubectl exec -it -n <namespace> <pod-name> -- sh -c \
  "sed -i 's/old-value/new-value/' /config/api-application.properties"
```

### Step 3: Apply Changes

**For dynamic properties** (loaded at runtime):
- Changes are immediate, no restart needed
- Check application logs to confirm

**For static properties** (loaded at startup):
```bash
kubectl rollout restart deployment/<deployment-name> -n <namespace>
```

## Common Configuration Updates

### Update Database Connection Pool
```bash
POD=$(kubectl get pods -n dev -l app=sentrius -o jsonpath='{.items[0].metadata.name}')

kubectl exec -it -n dev ${POD} -- sh -c "cat >> /config/api-application.properties << 'EOF'
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=10
EOF"

kubectl rollout restart deployment/sentrius-sentrius -n dev
```

### Update Keycloak URLs
```bash
POD=$(kubectl get pods -n dev -l app=sentrius -o jsonpath='{.items[0].metadata.name}')

kubectl exec -it -n dev ${POD} -- sh -c \
  "sed -i 's|keycloak.base-url=.*|keycloak.base-url=https://new-keycloak.example.com|' /config/api-application.properties"

kubectl rollout restart deployment/sentrius-sentrius -n dev
```

### Update Dynamic Properties
```bash
POD=$(kubectl get pods -n dev -l app=sentrius -o jsonpath='{.items[0].metadata.name}')

kubectl exec -it -n dev ${POD} -- vi /config/dynamic.properties
```

## Verification

### View Current Configuration
```bash
kubectl exec -it -n <namespace> <pod-name> -- cat /config/api-application.properties
```

### Check All Configuration Files
```bash
kubectl exec -it -n <namespace> <pod-name> -- ls -la /config/
```

### Verify Changes Applied
```bash
# Check application logs
kubectl logs -n <namespace> <pod-name> --tail=100 | grep -i "config\|property"

# Check if property is loaded
kubectl exec -it -n <namespace> <pod-name> -- env | grep SPRING
```

## Backup and Restore

### Backup Current Configuration
```bash
POD=$(kubectl get pods -n dev -l app=sentrius -o jsonpath='{.items[0].metadata.name}')
mkdir -p config-backup/$(date +%Y%m%d-%H%M%S)

# Backup all files
kubectl exec -n dev ${POD} -- tar czf - /config | \
  tar xzf - -C config-backup/$(date +%Y%m%d-%H%M%S)
```

### Restore Configuration
```bash
POD=$(kubectl get pods -n dev -l app=sentrius -o jsonpath='{.items[0].metadata.name}')

# Restore from backup
for file in config-backup/20241124-120000/config/*; do
  kubectl cp "${file}" "dev/${POD}:/config/$(basename ${file})"
done
```

## Troubleshooting

### Configuration Not Loading
```bash
# 1. Check PVC is bound
kubectl get pvc -n <namespace> | grep config-pvc

# 2. Check init job completed
kubectl get jobs -n <namespace> | grep config-init

# 3. View init job logs
kubectl logs -n <namespace> job/<release-name>-config-init

# 4. Verify files exist
kubectl exec -it -n <namespace> <pod-name> -- ls -la /config/
```

### Reset to Default Configuration
```bash
# This will re-run the init job and overwrite ALL files with ConfigMap values
helm upgrade <release-name> sentrius-chart --reuse-values
```

## Configuration Files Reference

### Main Chart (sentrius-chart)
- `api-application.properties` - API server configuration
- `agentproxy-application.properties` - Agent proxy settings
- `llmproxy-application.properties` - LLM proxy settings
- `ai-agent-application.properties` - AI agent settings
- `analysis-agent-application.properties` - Analysis agent settings
- `monitoring-agent-application.properties` - Monitoring agent settings
- `sshproxy-application.properties` - SSH proxy settings
- `rdpproxy-application.properties` - RDP proxy settings
- `dynamic.properties` - Runtime dynamic properties
- `default-policy.yaml` - Default agent policy
- `assessor-config.yaml` - Assessor configuration

### Launcher Chart (sentrius-chart-launcher)
- `launcher.properties` - Launcher service configuration
- `agent.properties` - Agent configuration
- `chat-helper.yaml` - Chat helper configuration
- `assessor-config.yaml` - Assessor configuration
- `chat-atpl-helper.yaml` - ATPL helper configuration

## Best Practices

1. ✅ **Always backup** before making changes
2. ✅ **Test in dev/staging** before production
3. ✅ **Document changes** for team visibility
4. ✅ **Monitor logs** after configuration updates
5. ✅ **Use version control** for configuration templates
6. ⚠️ **Avoid editing** while rolling updates are in progress
7. ⚠️ **Plan restarts** during maintenance windows

## Getting Help

For more detailed information, see:
- [Full Configuration Management Guide](./CONFIGURATION_MANAGEMENT.md)
- [Helm Chart Documentation](../sentrius-chart/README.md)
- [Troubleshooting Guide](./TROUBLESHOOTING.md)
