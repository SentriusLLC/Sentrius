# Helm Chart CI/CD Testing

This document describes the CI/CD testing capabilities for Sentrius Helm charts.

## Overview

Sentrius now includes comprehensive CI/CD testing for Helm charts to ensure:
- Chart validity and linting
- Template rendering correctness
- Multi-environment compatibility
- Schema validation
- Integration testing

## Automated CI/CD Testing

### GitHub Actions Workflows

Two workflows provide automated testing:

1. **`helm-ci.yml`** - Comprehensive Helm chart testing
2. **`maven.yml`** - Java build with improved test handling

#### Helm CI Workflow Features

- **Chart Linting**: Validates Helm chart syntax and best practices
- **Template Rendering**: Tests chart templates with different configurations
- **Schema Validation**: Ensures Chart.yaml files have required fields
- **Configuration Testing**: Tests charts with various value combinations
- **Integration Testing**: Uses Kind cluster for actual deployment testing (PR only)

#### Test Environments

The CI tests charts with multiple configurations:
- Local environment (`environment=local`)
- GKE environment (`environment=gke`)
- Different tenant configurations
- Various ingress settings

## Local Testing

### Quick Test Script

Use the provided test script for local development:

```bash
# Run all tests
./ops-scripts/test-helm-charts.sh

# Run specific test types
./ops-scripts/test-helm-charts.sh lint      # Lint charts only
./ops-scripts/test-helm-charts.sh template  # Test template rendering
./ops-scripts/test-helm-charts.sh schema    # Validate schemas
./ops-scripts/test-helm-charts.sh config    # Test configurations
./ops-scripts/test-helm-charts.sh deps      # Check dependencies
```

### Manual Testing Commands

```bash
# Lint individual charts
helm lint sentrius-chart
helm lint sentrius-chart-launcher

# Test template rendering
helm template test sentrius-chart-launcher --dry-run
helm template test sentrius-chart --set environment=local --dry-run

# Test with TLS enabled
helm template test sentrius-chart --set environment=local --set ingress.tlsEnabled=true --set certificates.enabled=true --dry-run

# Test with custom values
helm template test sentrius-chart-launcher \
  --set tenant=my-tenant \
  --set baseRelease=my-sentrius \
  --dry-run
```

## Known Issues

### ~~Sentrius Chart Ingress Template~~ (FIXED)

~~The main `sentrius-chart` has a known issue with the ingress template that causes linting failures. This is a YAML parsing issue in the conditional annotations section. The CI/CD pipeline handles this gracefully:~~

**UPDATE**: The ingress template YAML parsing issues have been resolved. The chart now passes linting and supports TLS configuration properly.

### Previous Workarounds (No Longer Needed)

~~Until the ingress template is fixed, you can:~~

1. ~~Use the `sentrius-chart-launcher` which works correctly~~
2. ~~Test `sentrius-chart` with `ingress.tlsEnabled=false`~~  
3. ~~Use the local deployment scripts which work around the issue~~

**All charts now work correctly with TLS enabled or disabled.**

## Chart Testing Best Practices

### For Developers

1. **Always test locally** before pushing:
   ```bash
   ./ops-scripts/test-helm-charts.sh
   ```

2. **Test with different environments**:
   - Local (`environment=local`)
   - GKE (`environment=gke`)
   - AWS (`environment=aws`)

3. **Validate template rendering** with various configurations

4. **Check for proper schema** in Chart.yaml files

### For CI/CD

1. **Linting runs on every push** and pull request
2. **Integration testing runs on pull requests** using Kind clusters
3. **Multiple configuration testing** ensures compatibility
4. **Graceful failure handling** for known issues

## Integration with Existing Deployment

The CI/CD testing complements existing deployment scripts:

- `ops-scripts/local/deploy-helm.sh` - Local deployment
- `ops-scripts/gcp/deploy-helm.sh` - GCP deployment  
- `ops-scripts/gcp/test-helm.sh` - GCP testing

The new testing ensures these scripts work with validated charts.

## Future Improvements

Potential enhancements for the CI/CD testing:

1. **Fix ingress template** YAML parsing issues
2. **Add security scanning** for Helm charts
3. **Performance testing** for large deployments
4. **Multi-cluster testing** for different Kubernetes versions
5. **Automated deployment** to staging environments

## Troubleshooting

### Common Issues

1. **Chart linting failures**: Usually YAML syntax or template issues
2. **Template rendering failures**: Often due to missing or invalid values
3. **Integration test failures**: May indicate resource conflicts or insufficient cluster resources

### Debug Commands

```bash
# Verbose linting
helm lint sentrius-chart --debug

# Template with debug output
helm template test sentrius-chart --debug

# Validate generated YAML
helm template test sentrius-chart-launcher | kubectl apply --dry-run=client -f -
```

## Conclusion

The new Helm chart CI/CD testing provides robust validation for Sentrius deployments, ensuring reliability and compatibility across different environments while maintaining development velocity.