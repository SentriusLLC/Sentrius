# Migration Guide: ConfigMap to PVC

## Summary

This migration replaces Kubernetes ConfigMaps with PersistentVolumeClaims (PVCs) for configuration file storage, enabling dynamic updates without pod restarts or full stack redeployment.

## What Changed

### Before (ConfigMap-based)
- Configuration stored in Kubernetes ConfigMap
- Any ConfigMap update required pod restart
- Rolling deployment triggered on every config change
- Downtime during configuration updates

### After (PVC-based)
- Configuration stored in ReadWriteMany PVC
- Initial configuration populated from ConfigMap via init job
- Updates applied directly to PVC files
- No pod restart required for most changes
- ConfigMap kept as template source

## Migration Process

### For New Deployments

Simply install with the updated Helm charts:

```bash
helm install sentrius sentrius-chart \
  --set tenant=my-tenant \
  --set config.storageClassName=nfs-storage  # if needed
```

The init job will automatically populate the PVC with configuration from the ConfigMap.

### For Existing Deployments

**Option 1: Upgrade (Recommended)**

1. Backup current configuration:
```bash
POD=$(kubectl get pods -n <namespace> -l app=sentrius -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n <namespace> ${POD} -- tar czf - /config > config-backup.tar.gz
```

2. Upgrade Helm release:
```bash
helm upgrade sentrius sentrius-chart --reuse-values
```

The pre-upgrade hook will:
- Create the PVC
- Run init job to populate PVC from ConfigMap
- Update deployments to mount PVC
- Restart pods with new volume mounts

3. Verify configuration files:
```bash
POD=$(kubectl get pods -n <namespace> -l app=sentrius -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n <namespace> ${POD} -- ls -la /config/
```

**Option 2: Fresh Install**

If you prefer a clean installation:

1. Export current ConfigMap values
2. Uninstall old release
3. Install new release with updated charts
4. Verify and restore any custom configuration

## Post-Migration

### Updating Configuration

**Old Way (no longer needed):**
```bash
# Edit values.yaml or use --set
helm upgrade sentrius sentrius-chart --set someConfig=newValue
# Wait for rolling deployment
```

**New Way:**
```bash
# Direct update to PVC
kubectl cp new-config.properties namespace/pod-name:/config/api-application.properties
# Changes visible immediately (or on next pod restart)
```

### Verifying Migration Success

1. Check PVC is bound:
```bash
kubectl get pvc -n <namespace> | grep config-pvc
# Should show: Bound
```

2. Check init job completed:
```bash
kubectl get jobs -n <namespace> | grep config-init
# Should show: 1/1 completions
```

3. Verify files in PVC:
```bash
POD=$(kubectl get pods -n <namespace> -l app=sentrius -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n <namespace> ${POD} -- ls -la /config/
```

4. Check pods are running:
```bash
kubectl get pods -n <namespace>
# All pods should be Running
```

## Rollback

If you need to rollback to ConfigMap-based configuration:

```bash
# Rollback to previous Helm revision
helm rollback sentrius <revision-number>

# Or install previous chart version
helm upgrade sentrius sentrius-chart --version <old-version>
```

Note: This will lose any PVC-only configuration changes.

## Storage Class Requirements

The PVC requires a storage class that supports `ReadWriteMany` access mode:

- **GKE**: Use `filestore.csi.storage.gke.io` (Google Filestore)
- **AWS**: Use `efs.csi.aws.com` (Amazon EFS)
- **Azure**: Use `azurefile` (Azure Files)
- **On-Prem**: Use NFS or similar network storage

If your default storage class doesn't support ReadWriteMany:

```bash
helm install sentrius sentrius-chart \
  --set config.storageClassName=nfs-storage
```

## Troubleshooting

### PVC Pending

**Symptom**: PVC stuck in Pending state

**Cause**: No storage class supports ReadWriteMany, or storage provisioner not available

**Solution**:
```bash
# Check available storage classes
kubectl get storageclass

# Specify a compatible storage class
helm upgrade sentrius sentrius-chart \
  --set config.storageClassName=<rwx-storage-class> \
  --reuse-values
```

### Init Job Failed

**Symptom**: config-init job shows failed status

**Solution**:
```bash
# Check job logs
kubectl logs -n <namespace> job/<release>-config-init

# Common fixes:
# - PVC not bound: Fix storage class issue
# - ConfigMap missing: Verify ConfigMap exists
# - Permissions: Check pod security policies
```

### Pods CrashLooping

**Symptom**: Pods crash after migration

**Solution**:
```bash
# Check pod logs
kubectl logs -n <namespace> <pod-name>

# Verify configuration files exist
kubectl exec -n <namespace> <pod-name> -- ls -la /config/

# If files missing, manually trigger init job
kubectl delete job <release>-config-init -n <namespace>
helm upgrade sentrius sentrius-chart --reuse-values
```

## FAQ

**Q: Will ConfigMap still be used?**
A: Yes, ConfigMap remains as the source template. The init job copies ConfigMap data to PVC on install/upgrade.

**Q: What happens to existing ConfigMap values?**
A: They are preserved and copied to the PVC during migration.

**Q: Can I still use Helm values for configuration?**
A: Yes, Helm values populate the ConfigMap, which is then copied to PVC by the init job.

**Q: Do I need to restart pods after updating PVC files?**
A: For static properties: Yes. For dynamic properties loaded at runtime: No.

**Q: What if I want to reset to default configuration?**
A: Run `helm upgrade` - the init job will overwrite PVC files with ConfigMap values.

**Q: Is the migration reversible?**
A: Yes, you can rollback via `helm rollback` or install an older chart version.

**Q: What about security/permissions?**
A: PVC uses same security context as pods. Use RBAC to control who can exec into pods.

## Best Practices

1. **Backup first**: Always backup configuration before migration
2. **Test in dev**: Migrate dev/staging environments before production
3. **Monitor closely**: Watch pod status and logs during migration
4. **Document changes**: Keep a record of PVC-only configuration changes
5. **Version control**: Maintain ConfigMap templates in git
6. **Regular backups**: Periodically backup PVC contents

## Support

For issues or questions:
- See [Configuration Management Guide](./CONFIGURATION_MANAGEMENT.md)
- See [Quick Reference Guide](./CONFIG_QUICK_REFERENCE.md)
- Check [Helm Chart Documentation](../sentrius-chart/README.md)
