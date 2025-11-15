# Kubernetes Pod Visualization Feature

## Overview
The K8s visualization layer allows users to view and manage Kubernetes pods deployed in tenant namespaces directly from the Sentrius web interface.

## Accessing the Feature
1. Navigate to the "Host Enclaves" page in Sentrius
2. Scroll to the "Deployed Pods" section at the bottom of the page

## Features

### Pod Listing
The system automatically displays all pods from both:
- Production namespace: `${TENANT}` (e.g., `dev`)
- Development namespace: `${TENANT}-dev` (e.g., `dev-dev`)

For each pod, you can see:
- **Pod Name**: The unique name of the pod
- **Namespace**: Which namespace the pod belongs to (production or dev)
- **Docker Image**: The container image being used
- **Status**: Current pod status (Running, Pending, Failed, etc.)

### Pod Operations

#### Restart Pod
Users with `CAN_MANAGE_SYSTEMS` permission can restart pods:
1. Click the "Restart Pod" button for the desired pod
2. Confirm the action in the dialog
3. The pod will be deleted and Kubernetes will automatically recreate it

**Note**: Restarting a pod deletes it with a 0-second grace period. Kubernetes will recreate the pod if it's managed by a Deployment, StatefulSet, or similar controller.

## Technical Architecture

### Backend Components

#### integration-proxy Module
- **KubernetesService**: Core service for K8s operations
  - `listAllPods()`: Lists pods from both namespaces
  - `listPodsInNamespace(namespace)`: Lists pods from specific namespace
  - `restartPod(namespace, podName)`: Restarts a specific pod
  
- **K8sController**: REST API endpoints
  - `GET /api/v1/k8s/pods`: List all pods
  - `GET /api/v1/k8s/pods/{namespace}`: List pods in namespace
  - `POST /api/v1/k8s/pods/{namespace}/{podName}/restart`: Restart pod

#### api Module
- **K8sApiController**: Proxy controller that routes requests to integration-proxy
  - `GET /api/v1/k8s/pods`: Proxied pod listing
  - `POST /api/v1/k8s/pods/{namespace}/{podName}/restart`: Proxied restart (requires CAN_MANAGE_SYSTEMS)

### Frontend Components
- **list_servers.html**: Updated with new "Deployed Pods" section
- **DataTable**: Interactive table with sorting and filtering
- **JavaScript functions**: 
  - Pod data fetching and display
  - Restart confirmation and execution

## Configuration

### Required Settings
The integration-proxy service URL is configured in `application.properties`:
```properties
sentrius.integration.proxyUrl=http://sentrius-integrationproxy:8080/
```

The tenant name is configured as:
```properties
sentrius.tenant=dev
```

### Kubernetes Access
The integration-proxy service must have access to the Kubernetes API server. This is typically configured via:
- Service account in Kubernetes
- KUBECONFIG file
- In-cluster configuration

## Security

### Permissions
- **Viewing pods**: Available to all authenticated users
- **Restarting pods**: Requires `CAN_MANAGE_SYSTEMS` permission

### Audit Trail
All pod operations are logged with:
- Username of the person performing the operation
- Timestamp
- Pod name and namespace
- Operation result (success/failure)

## Troubleshooting

### Pods Not Appearing
1. Verify namespaces exist: `kubectl get namespaces`
2. Check integration-proxy logs for errors
3. Verify RBAC permissions for the service account

### Restart Not Working
1. Confirm user has `CAN_MANAGE_SYSTEMS` permission
2. Check that pod is managed by a controller (Deployment/StatefulSet)
3. Review integration-proxy logs for API errors

### Empty Table
- If no namespaces exist, the table will be empty
- This is normal in development environments before deployment

## API Examples

### List All Pods
```bash
curl -X GET http://sentrius-api:8080/api/v1/k8s/pods \
  -H "Authorization: Bearer <token>"
```

### Restart a Pod
```bash
curl -X POST http://sentrius-api:8080/api/v1/k8s/pods/dev/my-pod-123/restart \
  -H "Authorization: Bearer <token>" \
  -H "X-CSRF-TOKEN: <csrf-token>"
```

## Future Enhancements
Potential additions for this feature:
- Pod logs viewing
- Pod shell access
- Resource usage metrics
- Pod scaling controls
- Event history for pods
