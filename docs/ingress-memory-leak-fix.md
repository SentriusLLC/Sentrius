# Nginx Ingress Memory Leak and File Descriptor Fix

## Problem Statement

Nginx ingress controller experiencing memory leaks and excessive file descriptor usage, potentially caused by:
- Unbounded buffer allocations
- Connection pooling without limits
- Missing keepalive connection management
- No rate limiting on connections per IP

## Solution Overview

Added comprehensive nginx ingress annotations to both `sentrius-chart` and `sentrius-chart-launcher` Helm charts to prevent memory leaks and manage file descriptors efficiently.

## Changes Made

### 1. Buffer Size Configurations

Added buffer size limits to prevent memory bloat:

```yaml
nginx.ingress.kubernetes.io/proxy-buffer-size: "8k"
nginx.ingress.kubernetes.io/proxy-buffers-number: "4"
nginx.ingress.kubernetes.io/client-body-buffer-size: "8k"
```

**Impact**: Limits memory usage per connection to approximately 32KB (4 buffers × 8KB), preventing unbounded memory growth.

### 2. Connection and Timeout Settings

Configured timeouts to prevent resource exhaustion:

```yaml
nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
nginx.ingress.kubernetes.io/proxy-send-timeout: "60"
nginx.ingress.kubernetes.io/proxy-connect-timeout: "60"
```

**Note**: `sentrius-chart-launcher` uses higher timeouts (3600s) for WebSocket support.

**Impact**: Ensures connections are closed after inactivity, preventing file descriptor accumulation.

### 3. Keepalive Connection Management

Added upstream keepalive settings to reuse connections efficiently:

```yaml
nginx.ingress.kubernetes.io/upstream-keepalive-connections: "32"
nginx.ingress.kubernetes.io/upstream-keepalive-timeout: "60"
nginx.ingress.kubernetes.io/upstream-keepalive-requests: "100"
```

**Impact**: 
- Maintains a pool of 32 reusable connections to upstream services
- Closes keepalive connections after 60 seconds of inactivity
- Reuses each connection up to 100 times before recycling
- Reduces connection overhead and file descriptor churn

### 4. Request Size Limits

Set maximum request body size:

```yaml
nginx.ingress.kubernetes.io/proxy-body-size: "10m"
```

**Impact**: Prevents large payloads from consuming excessive memory.

### 5. Connection Rate Limiting

Added per-IP connection limits:

```yaml
nginx.ingress.kubernetes.io/limit-connections: "10"
```

**Impact**: Prevents a single client from exhausting server resources.

## Configuration

All settings are now configurable via `values.yaml`:

### sentrius-chart

```yaml
ingress:
  nginx:
    proxyBufferSize: "8k"
    proxyBuffersNumber: "4"
    clientBodyBufferSize: "8k"
    proxyReadTimeout: "60"
    proxySendTimeout: "60"
    proxyConnectTimeout: "60"
    upstreamKeepaliveConnections: "32"
    upstreamKeepaliveTimeout: "60"
    upstreamKeepaliveRequests: "100"
    proxyBodySize: "10m"
    limitConnections: "10"
```

### sentrius-chart-launcher

```yaml
ingress:
  nginx:
    proxyBufferSize: "8k"
    proxyBuffersNumber: "4"
    clientBodyBufferSize: "8k"
    proxyReadTimeout: "3600"  # High for WebSockets
    proxySendTimeout: "3600"  # High for WebSockets
    proxyConnectTimeout: "60"
    upstreamKeepaliveConnections: "32"
    upstreamKeepaliveTimeout: "60"
    upstreamKeepaliveRequests: "100"
    proxyBodySize: "10m"
    limitConnections: "10"
```

## Validation

### Helm Lint

Both charts pass validation:

```bash
cd /path/to/sentrius
helm lint sentrius-chart
helm lint sentrius-chart-launcher
```

### Template Rendering

Verify annotations are properly applied:

```bash
helm template test-sentrius sentrius-chart --set tenant=test-tenant | grep -A 30 "kind: Ingress"
```

### Deployment Testing

1. Deploy with updated charts:
   ```bash
   helm upgrade --install sentrius sentrius-chart \
     --set tenant=your-tenant \
     --namespace your-namespace
   ```

2. Monitor nginx ingress controller memory:
   ```bash
   kubectl top pods -n ingress-nginx
   ```

3. Check file descriptor usage:
   ```bash
   kubectl exec -n ingress-nginx <nginx-controller-pod> -- \
     sh -c 'ls -l /proc/$$/fd | wc -l'
   ```

## Expected Results

After deploying these changes:

1. **Memory Usage**: Should stabilize and not grow unbounded over time
2. **File Descriptors**: Should remain within reasonable limits (typically < 1000 for moderate load)
3. **Connection Efficiency**: Improved through keepalive connection reuse
4. **Resource Protection**: Rate limiting prevents resource exhaustion from individual clients

## Monitoring Recommendations

1. **Memory Monitoring**:
   ```bash
   kubectl top pods -n ingress-nginx --watch
   ```

2. **Connection Metrics**:
   - Monitor active connections in nginx metrics
   - Track connection pooling efficiency
   - Watch for connection timeout events

3. **File Descriptor Tracking**:
   ```bash
   # Inside nginx controller pod
   watch -n 5 'ls -l /proc/$$/fd | wc -l'
   ```

4. **Rate Limit Metrics**:
   - Check nginx access logs for 503 errors (rate limit exceeded)
   - Adjust `limitConnections` if legitimate traffic is being blocked

## Tuning Guidelines

### When to Increase Buffer Sizes

If you see frequent 502/504 errors with large responses:
```yaml
proxyBufferSize: "16k"
proxyBuffersNumber: "8"
```

### When to Adjust Keepalive Settings

For high-traffic environments:
```yaml
upstreamKeepaliveConnections: "64"  # More pooled connections
upstreamKeepaliveRequests: "200"   # Reuse connections more
```

For low-traffic environments:
```yaml
upstreamKeepaliveConnections: "16"  # Fewer pooled connections
upstreamKeepaliveTimeout: "30"      # Close idle connections faster
```

### When to Adjust Connection Limits

For API-heavy workloads from known clients:
```yaml
limitConnections: "50"  # Allow more concurrent connections per IP
```

For public-facing services with DDoS concerns:
```yaml
limitConnections: "5"   # Stricter rate limiting
```

## Rollback Instructions

If issues arise, revert to default behavior by removing the nginx section from ingress configuration:

```bash
helm upgrade sentrius sentrius-chart \
  --set ingress.nginx.proxyBufferSize="" \
  --reuse-values
```

Or rollback to previous release:

```bash
helm rollback sentrius
```

## References

- [Nginx Ingress Controller Annotations](https://kubernetes.github.io/ingress-nginx/user-guide/nginx-configuration/annotations/)
- [Nginx Buffer Configuration](http://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffers)
- [Nginx Keepalive](http://nginx.org/en/docs/http/ngx_http_upstream_module.html#keepalive)
- [Nginx Connection Limiting](http://nginx.org/en/docs/http/ngx_http_limit_conn_module.html)
