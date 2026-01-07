# Example Agent Template Launch Configurations

This document provides practical examples of launch configurations using the new image resolution feature.

## Example 1: Basic Explicit Tag

Simple configuration with explicit tag specification:

```json
{
  "imageIntent": {
    "tag": "v1.2.3"
  },
  "resources": {
    "cpu": "500m",
    "memory": "1Gi"
  }
}
```

**Use Case**: Deploying a specific stable version for production agents.

## Example 2: Custom Registry with Generation Strategy

Using a custom registry with generation-based image selection:

```json
{
  "imageIntent": {
    "repo": "ghcr.io/sentrius/agents/custom-agent",
    "selection": {
      "strategy": "generation",
      "maxGeneration": 5
    }
  },
  "resources": {
    "cpu": "1000m",
    "memory": "2Gi"
  }
}
```

**Use Case**: Using generation-based versioning where each generation represents a major iteration or model version.

## Example 3: Latest with High Resources

Always use the latest version with increased resources:

```json
{
  "imageIntent": {
    "selection": {
      "strategy": "latest"
    }
  },
  "resources": {
    "cpu": "2000m",
    "memory": "4Gi"
  }
}
```

**Use Case**: Development/testing environments where you always want the latest features.

## Example 4: Specific Tag Strategy

Using a specific tag from selection strategy:

```json
{
  "imageIntent": {
    "selection": {
      "strategy": "tag",
      "specificTag": "stable"
    }
  },
  "resources": {
    "cpu": "1000m",
    "memory": "2Gi"
  }
}
```

**Use Case**: Deploying from a floating "stable" tag that's updated by CI/CD.

## Example 5: Minimal Resources Configuration

Lightweight agent with minimal resources:

```json
{
  "resources": {
    "cpu": "250m",
    "memory": "512Mi",
    "cpuRequest": "100m",
    "memoryRequest": "256Mi"
  }
}
```

**Use Case**: Simple monitoring or utility agents that don't need much compute power.

## Example 6: Full Configuration with Requirements

Complete configuration with all fields:

```json
{
  "imageIntent": {
    "repo": "ghcr.io/sentrius/agents/security-scanner",
    "selection": {
      "strategy": "generation",
      "maxGeneration": 3,
      "minGeneration": 2
    },
    "requirements": {
      "signed": true,
      "agentNameMatch": true,
      "minVersion": "2.0.0"
    }
  },
  "resources": {
    "cpu": "1500m",
    "memory": "3Gi",
    "cpuRequest": "500m",
    "memoryRequest": "1Gi"
  },
  "restartPolicy": "Never"
}
```

**Use Case**: Production security agents with strict verification requirements.

## Example 7: No Configuration (Backward Compatible)

Empty or null configuration - uses system defaults:

```json
null
```

or

```json
{}
```

**Resolved to**: 
- Image: `{configured-registry}/sentrius-launchable-agent:{configured-version}`
- CPU: 2000m
- Memory: 2Gi

**Use Case**: Maintains backward compatibility with existing templates.

## Using in Agent Templates

### Via API

When creating or updating an agent template:

```bash
POST /api/v1/agent/templates
Content-Type: application/json

{
  "name": "High-Performance Chat Agent",
  "description": "Chat agent with increased resources",
  "agentType": "chat",
  "category": "Communication",
  "icon": "fa-comments",
  "defaultConfiguration": "{...}",
  "launchConfiguration": "{\"imageIntent\":{\"tag\":\"v2.0.0\"},\"resources\":{\"cpu\":\"2000m\",\"memory\":\"4Gi\"}}"
}
```

### Via prepare-launch Endpoint

The configuration is automatically included when preparing a launch:

```bash
POST /api/v1/agent/templates/{templateId}/prepare-launch?agentName=my-agent
Authorization: Bearer {token}
```

Response includes:
```json
{
  "agentName": "my-agent",
  "agentType": "chat",
  "templateLaunchConfiguration": "{\"imageIntent\":{\"tag\":\"v2.0.0\"},\"resources\":{\"cpu\":\"2000m\",\"memory\":\"4Gi\"}}"
}
```

### In Database

When inserting/updating agent templates directly:

```sql
UPDATE agent_templates 
SET launch_configuration = '{"imageIntent":{"tag":"v2.0.0"},"resources":{"cpu":"2000m","memory":"4Gi"}}'::jsonb
WHERE name = 'Chat Assistant';
```

## Testing Configurations

To test a configuration without deploying:

```bash
# Check what image would be resolved
curl -X POST http://localhost:8080/api/v1/agent/templates/{id}/prepare-launch?agentName=test \
  -H "Authorization: Bearer {token}" | jq '.templateLaunchConfiguration'
```

## Migration from Existing Agents

For existing agents without `templateLaunchConfiguration`:
1. They continue to work with defaults
2. No migration required
3. New configurations can be added incrementally

To add configuration to existing templates:

```sql
UPDATE agent_templates 
SET launch_configuration = '{"resources":{"cpu":"1000m","memory":"2Gi"}}'::jsonb
WHERE launch_configuration IS NULL 
  AND agent_type = 'chat';
```

## Best Practices

1. **Use explicit tags for production**: Avoid "latest" in production environments
2. **Set resource limits**: Always specify resource limits to prevent runaway agents
3. **Test before deploying**: Use the prepare-launch endpoint to verify configurations
4. **Document version changes**: When updating image tags, document what changed
5. **Use generation strategy for ML models**: When deploying ML models, generation numbers indicate model training iterations
6. **Keep fallbacks simple**: Don't over-complicate configurations; simpler is better for debugging

## Troubleshooting

### Agent fails to launch with image resolution error

Check logs for:
- Invalid JSON in `templateLaunchConfiguration`
- Registry accessibility issues
- Image tag doesn't exist

### Resources not being applied

Verify:
- JSON format is correct
- Field names match exactly: `cpu`, `memory`
- Values use Kubernetes resource format (e.g., "500m", "1Gi")

### Backward compatibility issues

If existing agents fail after upgrade:
- Check if they have null or empty `templateLaunchConfiguration` (should work)
- Verify fallback configuration values are set correctly
- Review logs for image resolution warnings
