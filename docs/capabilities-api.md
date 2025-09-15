# Endpoint Capabilities API

This document describes the new endpoint capabilities API that provides a unified view of all REST endpoints and Verb methods available in the Sentrius system.

## Overview

The capabilities API allows AI agents, Python agents, and other systems to dynamically discover what operations are available across the entire Sentrius platform. It scans both:

- **REST API endpoints** from Spring controllers with `@LimitAccess` annotations
- **Verb methods** from AI agent classes with `@Verb` annotations

## API Endpoints

All endpoints are secured with `@LimitAccess` and require appropriate authentication.

### Get All Endpoints

```
GET /api/v1/capabilities/endpoints
```

Returns all available endpoints (both REST and Verb) with optional filtering:

**Query Parameters:**
- `type` (optional): Filter by type (`REST` or `VERB`)
- `requiresAuth` (optional): Filter by authentication requirement (`true` or `false`)

**Example Response:**
```json
[
  {
    "name": "listusers", 
    "description": "Returns list of users",
    "type": "REST",
    "httpMethod": "GET",
    "path": "/api/v1/users/list",
    "className": "io.sentrius.sso.controllers.api.users.UserApiController",
    "methodName": "listusers",
    "requiresAuthentication": true,
    "accessLimitations": {
      "hasLimitAccess": true,
      "userAccess": ["CAN_VIEW_USERS"]
    },
    "parameters": [...]
  },
  {
    "name": "assess_ztat_requests",
    "description": "Analyzes ztats requests according to the by prompting the LLM",
    "type": "VERB", 
    "className": "io.sentrius.agent.analysis.agents.verbs.AgentVerbs",
    "methodName": "analyzeAtatRequests",
    "requiresTokenManagement": true,
    "metadata": {
      "isAiCallable": true,
      "outputInterpreter": "...",
      "inputInterpreter": "..."
    }
  }
]
```

### Get REST Endpoints Only

```
GET /api/v1/capabilities/rest
```

Returns only REST API endpoints from Spring controllers.

### Get Verb Methods Only

```
GET /api/v1/capabilities/verbs
```

Returns only Verb methods available to AI agents.

### Refresh Cache

```
GET /api/v1/capabilities/refresh
```

Forces a refresh of the endpoint cache. Useful during development or after deploying new capabilities.

**Requires:** `CAN_MANAGE_APPLICATION` access level.

## Data Model

### EndpointDescriptor

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Endpoint/verb name |
| `description` | String | Human-readable description |
| `type` | String | Either "REST" or "VERB" |
| `httpMethod` | String | HTTP method (GET, POST, etc.) - REST only |
| `path` | String | URL path - REST only |
| `className` | String | Java class containing the method |
| `methodName` | String | Java method name |
| `parameters` | List | Parameter descriptors |
| `accessLimitations` | Object | Access control information |
| `requiresAuthentication` | Boolean | Whether authentication is required |
| `requiresTokenManagement` | Boolean | Whether token management is needed |
| `returnType` | Class | Method return type |
| `metadata` | Map | Additional metadata (mainly for Verbs) |

### AccessLimitations

Extracted from `@LimitAccess` annotations:

| Field | Type | Description |
|-------|------|-------------|
| `hasLimitAccess` | Boolean | Whether @LimitAccess annotation is present |
| `userAccess` | Array | Required user access levels |
| `applicationAccess` | Array | Required application access levels |
| `sshAccess` | Array | Required SSH access levels |
| `allowedIdentityTypes` | Array | Allowed identity types |
| `endpointThreat` | String | Endpoint threat level |

## Usage Examples

### For AI Agents

AI agents can discover available verbs:

```bash
curl -H "Authorization: Bearer <token>" \
  "/api/v1/capabilities/verbs"
```

### For Python Agents

Python agents can discover all capabilities:

```python
import requests

response = requests.get(
    "https://sentrius.example.com/api/v1/capabilities/endpoints",
    headers={"Authorization": f"Bearer {token}"}
)

capabilities = response.json()
for cap in capabilities:
    print(f"{cap['type']}: {cap['name']} - {cap['description']}")
```

### For Dynamic Documentation

Generate API documentation dynamically:

```bash
curl -H "Authorization: Bearer <token>" \
  "/api/v1/capabilities/rest?requiresAuth=true"
```

## Integration

### VerbRegistry Integration

The `VerbRegistry` class now provides additional methods:

```java
@Autowired
private VerbRegistry verbRegistry;

// Get all verb descriptors
List<EndpointDescriptor> allVerbs = verbRegistry.getVerbDescriptors();

// Get only AI-callable verbs  
List<EndpointDescriptor> aiVerbs = verbRegistry.getAiCallableVerbDescriptors();
```

### Custom Scanning

The `EndpointScanningService` can be used directly:

```java
@Autowired
private EndpointScanningService scanningService;

// Get all endpoints
List<EndpointDescriptor> endpoints = scanningService.getAllEndpoints();

// Force refresh
scanningService.refreshEndpoints();
```

## Performance

- Results are cached for performance
- Cache is automatically populated on first request
- Cache can be manually refreshed via the `/refresh` endpoint
- Scanning happens at startup and on-demand

## Security

- All endpoints require authentication
- Access limitations from `@LimitAccess` are preserved and exposed
- Endpoint access follows the same security model as the underlying APIs
- The refresh endpoint requires `CAN_MANAGE_APPLICATION` permission

## Development Notes

- The scanning covers packages starting with `io.sentrius`
- New controllers and verbs are automatically discovered
- Changes require cache refresh or application restart to be visible
- Comprehensive logging is available for debugging scanning issues