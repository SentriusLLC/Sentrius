# Agent Image Resolution

## Overview

The agent launcher service now supports flexible image resolution through the `imageIntent` configuration in the `templateLaunchConfiguration` field of `AgentRegistrationDTO`. This allows for dynamic image selection based on various strategies including generation-based versioning, explicit tags, or latest versions.

## Configuration Structure

The `templateLaunchConfiguration` field accepts a JSON object with the following structure:

```json
{
  "imageIntent": {
    "repo": "ghcr.io/sentrius/agents/payments",
    "tag": "v1.0.0",
    "selection": {
      "strategy": "generation",
      "maxGeneration": 4,
      "minGeneration": 1,
      "specificTag": "stable"
    },
    "requirements": {
      "signed": true,
      "agentNameMatch": true,
      "minVersion": "1.0.0"
    }
  },
  "resources": {
    "cpu": "500m",
    "memory": "1Gi",
    "cpuRequest": "250m",
    "memoryRequest": "512Mi"
  },
  "restartPolicy": "Never"
}
```

## Image Intent Fields

### `repo` (optional)
- **Type**: String
- **Description**: The container registry repository path
- **Example**: `"ghcr.io/sentrius/agents/payments"`
- **Default**: Uses configured `sentrius.agent.registry` + `sentrius-launchable-agent`

### `tag` (optional)
- **Type**: String
- **Description**: Explicitly specified image tag (overrides selection strategy)
- **Example**: `"v1.0.0"`, `"latest"`, `"stable"`
- **When specified**: Bypasses selection strategy and uses this tag directly

### `selection` (optional)
Configuration for dynamic image selection strategy.

#### `strategy` (required if selection is specified)
- **Type**: String
- **Values**: `"generation"`, `"latest"`, `"tag"`
- **Description**: The strategy to use for selecting an image

##### Strategy: `generation`
Uses generation-based versioning to select images.
```json
{
  "strategy": "generation",
  "maxGeneration": 4,
  "minGeneration": 1
}
```
- `maxGeneration`: Maximum generation number to select (constructs tag as `gen-{n}`)
- `minGeneration`: Minimum generation number (for future filtering)

**Example resolved image**: `sentrius-launchable-agent:gen-4`

##### Strategy: `latest`
Selects the latest available image.
```json
{
  "strategy": "latest"
}
```

**Example resolved image**: `sentrius-launchable-agent:latest`

##### Strategy: `tag`
Uses a specific tag from configuration.
```json
{
  "strategy": "tag",
  "specificTag": "stable"
}
```

**Example resolved image**: `sentrius-launchable-agent:stable`

### `requirements` (optional)
Image verification criteria (for future enhancement).

- `signed`: Require image to be signed (boolean)
- `agentNameMatch`: Require agent name to match in image metadata (boolean)
- `minVersion`: Minimum required version (string)

## Resource Configuration

The `resources` section allows specifying pod resource limits and requests:

```json
{
  "cpu": "500m",
  "memory": "1Gi",
  "cpuRequest": "250m",
  "memoryRequest": "512Mi"
}
```

- **`cpu`**: CPU limit (e.g., "500m", "1", "2000m")
- **`memory`**: Memory limit (e.g., "512Mi", "1Gi", "2Gi")
- **`cpuRequest`**: CPU request (optional, defaults to limit)
- **`memoryRequest`**: Memory request (optional, defaults to limit)

If not specified, defaults to:
- CPU: `2000m`
- Memory: `2Gi`

## Resolution Order

The image resolver follows this priority order:

1. **Explicit Tag**: If `tag` is specified, use `repo:tag`
2. **Selection Strategy**: If `selection` is specified, apply the strategy
3. **Fallback**: Use configured `sentrius.agent.registry` + `sentrius.agent.registry.version`

## Usage Examples

### Example 1: Explicit Tag
```json
{
  "imageIntent": {
    "tag": "v1.2.3"
  }
}
```
**Resolves to**: `sentrius-launchable-agent:v1.2.3`

### Example 2: Custom Repository with Tag
```json
{
  "imageIntent": {
    "repo": "ghcr.io/myorg/custom-agent",
    "tag": "production"
  }
}
```
**Resolves to**: `ghcr.io/myorg/custom-agent:production`

### Example 3: Generation-Based Selection
```json
{
  "imageIntent": {
    "selection": {
      "strategy": "generation",
      "maxGeneration": 5
    }
  }
}
```
**Resolves to**: `sentrius-launchable-agent:gen-5`

### Example 4: Latest with Custom Resources
```json
{
  "imageIntent": {
    "selection": {
      "strategy": "latest"
    }
  },
  "resources": {
    "cpu": "1000m",
    "memory": "2Gi"
  }
}
```
**Resolves to**: `sentrius-launchable-agent:latest` with 1000m CPU and 2Gi memory

### Example 5: Full Configuration
```json
{
  "imageIntent": {
    "repo": "ghcr.io/sentrius/agents/payments",
    "selection": {
      "strategy": "generation",
      "maxGeneration": 4
    },
    "requirements": {
      "signed": true,
      "agentNameMatch": true
    }
  },
  "resources": {
    "cpu": "500m",
    "memory": "1Gi"
  }
}
```

### Example 6: No Configuration (Fallback)
If no `templateLaunchConfiguration` is provided or `imageIntent` is empty:
```json
{}
```
**Resolves to**: `{configured-registry}/sentrius-launchable-agent:{configured-version}`

## API Integration

### Preparing Agent Launch
When preparing an agent launch from a template, include the launch configuration:

```bash
POST /api/v1/agent/templates/{templateId}/prepare-launch?agentName=my-agent
```

The response `AgentRegistrationDTO` will include the `templateLaunchConfiguration` field.

### Launching Agent
Pass the full `AgentRegistrationDTO` to the launcher:

```bash
POST /api/v1/agent/launcher/create
Authorization: Bearer {token}
Content-Type: application/json

{
  "agentName": "my-agent",
  "agentType": "chat",
  "clientId": "client-id",
  "templateLaunchConfiguration": "{\"imageIntent\":{...}, \"resources\":{...}}"
}
```

## Configuration Properties

The launcher service uses these properties for fallback behavior:

- `sentrius.agent.registry`: Base registry URL (or "local")
- `sentrius.agent.registry.version`: Default version tag
- `sentrius.agent.namespace`: Kubernetes namespace for agents

## Backward Compatibility

If no `templateLaunchConfiguration` is provided, the launcher falls back to:
- Image: `{sentrius.agent.registry}/sentrius-launchable-agent:{sentrius.agent.registry.version}`
- Resources: CPU 2000m, Memory 2Gi

This ensures existing agents continue to work without modification.

## Image Resolution Implementation

The resolver queries the Kubernetes cluster to discover available container images that have been pulled to nodes. This provides validation and selection capabilities:

### Kubernetes-Based Image Discovery

The resolver uses the Kubernetes API to:
1. Query all nodes in the cluster via `CoreV1Api.listNode()`
2. Inspect `node.status.images` to find available container images
3. Match images by repository prefix to find candidates
4. Select the best match based on the configured strategy

### Resolution Behavior

**Generation Strategy:**
- Queries Kubernetes for images matching the repo pattern `repo:gen-N`
- Finds the highest generation within the specified range (minGeneration to maxGeneration)
- Falls back to constructing `gen-{maxGeneration}` tag if no matches found
- Gracefully handles Kubernetes API failures by using constructed tags

**Latest Strategy:**
- Checks if `repo:latest` exists in the cluster
- Uses "latest" tag regardless of cluster state (with appropriate logging)
- Falls back gracefully if Kubernetes query fails

**Tag Strategy:**
- Uses the specified tag directly without querying

### Fallback Chain

For all strategies, the resolver follows this graceful degradation:
1. Attempt to query Kubernetes for existing images
2. If query fails or no images found, construct tag based on configuration
3. Log warnings for failures but continue with fallback
4. Always return a valid image reference

This approach ensures:
- ✅ Works in production with full Kubernetes access
- ✅ Works in development/test environments without Kubernetes
- ✅ Provides visibility into available images when possible
- ✅ Never blocks launches due to discovery failures

## Future Enhancements

The following features are planned for future releases:

1. **Direct Registry API Querying**: Query container registries via Docker Registry HTTP API v2 for comprehensive image discovery
2. **Image Verification**: Implement signature verification and metadata validation
3. **Semantic Versioning**: Support semantic version constraints (e.g., "^1.0.0", "~2.1.0")
4. **Multi-Registry Support**: Query multiple registries with priority ordering
5. **Caching**: Cache resolved images to improve performance
6. **Metrics**: Track image resolution success/failure rates
