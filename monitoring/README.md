# Monitoring Agent

The Monitoring Agent is a specialized NPE (Non-Person Entity) agent that extends the enterprise-agent package to provide comprehensive service monitoring, OpenTelemetry trace analysis, and intelligent notification capabilities.

## Features

### Core Capabilities
- **OpenTelemetry Integration**: Query and analyze OTel traces for service health metrics
- **Endpoint Monitoring**: Continuous health checks for registered endpoints
- **AI/ML-Based Analysis**: Intelligent stability evaluation using multiple data points
- **Multi-Channel Notifications**: Support for internal UI, JIRA, PagerDuty, Slack, and email notifications
- **NPE Agent Identity**: Registered as a Non-Person Entity with its own identity and heartbeat

### Monitoring Features
- Real-time endpoint health tracking
- Error rate calculation and trending
- Latency monitoring and anomaly detection
- Throughput analysis
- Configurable thresholds for alerts
- Trend-based alerting (wait for pattern confirmation)

### Notification System
- **Internal**: Stored notifications viewable in Sentrius UI
- **JIRA**: Automatic issue creation for critical events
- **PagerDuty**: Incident triggering and management
- **Slack**: Real-time alerts to Slack channels
- **Email**: SMTP-based email notifications

## Architecture

```
monitoring/
├── src/main/java/io/sentrius/agent/monitoring/
│   ├── MonitoringAgent.java                    # Main Spring Boot application
│   ├── config/
│   │   └── MonitoringAgentConfig.java          # Configuration beans
│   ├── model/
│   │   ├── EndpointHealth.java                 # Health status model
│   │   └── MonitoringConfig.java               # Configuration model
│   └── service/
│       ├── OtelTraceQueryService.java          # OpenTelemetry trace querying
│       ├── EndpointMonitoringService.java      # Endpoint health checking
│       ├── NotificationService.java            # Multi-channel notifications
│       ├── ServiceStabilityEvaluationService.java # AI/ML stability analysis
│       └── RegisteredMonitoringAgent.java      # NPE agent implementation
└── src/main/resources/
    └── application.properties                   # Configuration
```

## Configuration

### Application Properties

```properties
# Enable the monitoring agent
agents.monitoring.enabled=true
agents.monitoring.name=monitoring-agent
agents.monitoring.check-interval=60000

# OpenTelemetry Configuration
otel.traces.exporter=otlp
otel.exporter.otlp.endpoint=http://localhost:4317
otel.service.name=monitoring-agent

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/sentrius
spring.datasource.username=postgres
spring.datasource.password=password

# Agent API Configuration
agent.api.url=http://localhost:8080
```

### Monitoring Configuration Model

```java
{
  "endpointUrl": "http://api.example.com/health",
  "serviceName": "example-api",
  "responseTimeThreshold": 1000,        // milliseconds
  "errorRateThreshold": 5.0,            // percentage
  "latencyThreshold": 500.0,            // milliseconds
  "analysisWindowMinutes": 5,
  "waitForTrend": false,
  "notifyOnDown": true,
  "notifyOnSlowResponse": true,
  "notifyOnHighErrors": true,
  "notifyOnHighLatency": true,
  "notificationChannels": ["INTERNAL", "SLACK"],
  "useAiEvaluation": true
}
```

## API Endpoints

### Configuration Management

- `GET /api/v1/monitoring/config` - List all monitoring configurations
- `GET /api/v1/monitoring/config/{id}` - Get specific configuration
- `POST /api/v1/monitoring/config` - Create new monitoring configuration
- `PUT /api/v1/monitoring/config/{id}` - Update configuration
- `DELETE /api/v1/monitoring/config/{id}` - Delete configuration

### Health Metrics

- `GET /api/v1/monitoring/health/{endpointUrl}?limit=10` - Get health metrics for endpoint

### Notifications

- `GET /api/v1/monitoring/notifications?limit=50&acknowledged=false` - Get recent notifications
- `POST /api/v1/monitoring/notifications/{id}/acknowledge?acknowledgedBy=username` - Acknowledge notification

## Usage Examples

### Register an Endpoint for Monitoring

```bash
curl -X POST http://localhost:8080/api/v1/monitoring/config \
  -H "Content-Type: application/json" \
  -d '{
    "endpointUrl": "http://sentrius-api:8080/actuator/health",
    "serviceName": "sentrius-api",
    "responseTimeThreshold": 1000,
    "errorRateThreshold": 5.0,
    "latencyThreshold": 500.0,
    "analysisWindowMinutes": 5,
    "notifyOnDown": true,
    "notifyOnHighErrors": true,
    "notificationChannels": ["INTERNAL", "SLACK"]
  }'
```

### Get Health Metrics

```bash
curl http://localhost:8080/api/v1/monitoring/health/http%3A%2F%2Fsentrius-api%3A8080%2Factuator%2Fhealth?limit=10
```

### Get Unacknowledged Notifications

```bash
curl http://localhost:8080/api/v1/monitoring/notifications?acknowledged=false&limit=50
```

### Acknowledge a Notification

```bash
curl -X POST "http://localhost:8080/api/v1/monitoring/notifications/123/acknowledge?acknowledgedBy=admin"
```

## Database Schema

### agent_monitoring_config
Stores monitoring configuration for endpoints.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| endpoint_url | VARCHAR(500) | URL to monitor |
| service_name | VARCHAR(255) | Service name for OTel traces |
| response_time_threshold | BIGINT | Max acceptable response time (ms) |
| error_rate_threshold | DOUBLE | Max acceptable error rate (%) |
| latency_threshold | DOUBLE | Max acceptable latency (ms) |
| notification_channels | TEXT | Comma-separated channel list |
| enabled | BOOLEAN | Whether monitoring is active |

### endpoint_health_metrics
Historical health metrics for endpoints.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| endpoint_url | VARCHAR(500) | URL monitored |
| status | VARCHAR(50) | HEALTHY, DEGRADED, DOWN |
| response_time | BIGINT | Response time (ms) |
| error_rate | DOUBLE | Error rate (%) |
| checked_at | TIMESTAMP | When check was performed |

### notification_history
Record of all notifications sent.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| title | VARCHAR(255) | Notification title |
| message | TEXT | Notification message |
| severity | VARCHAR(50) | INFO, WARNING, ERROR, CRITICAL |
| channel | VARCHAR(50) | Channel used |
| sent_at | TIMESTAMP | When sent |
| acknowledged | BOOLEAN | Whether acknowledged |

## Running the Monitoring Agent

### Standalone Mode

```bash
cd monitoring
mvn spring-boot:run
```

### With Docker

```bash
docker build -t monitoring-agent -f monitoring/Dockerfile .
docker run -e DATABASE_PASSWORD=password \
           -e KEYCLOAK_BASE_URL=http://keycloak:8180 \
           -e OTEL_ENDPOINT=http://otel-collector:4317 \
           monitoring-agent
```

### With Kubernetes

The monitoring agent can be deployed as part of the Sentrius Helm chart or as a standalone deployment.

```bash
helm install monitoring-agent ./sentrius-chart \
  --set monitoring.enabled=true \
  --set monitoring.otelEndpoint=http://otel-collector:4317
```

## Notification Channel Configuration

### JIRA Integration

To enable JIRA notifications, configure the notification channel:

```sql
UPDATE notification_channel_config 
SET enabled = TRUE,
    configuration = '{
      "url": "https://your-instance.atlassian.net",
      "project_key": "MON",
      "api_token": "your-api-token"
    }'::jsonb
WHERE channel_name = 'JIRA';
```

### PagerDuty Integration

```sql
UPDATE notification_channel_config 
SET enabled = TRUE,
    configuration = '{
      "integration_key": "your-integration-key"
    }'::jsonb
WHERE channel_name = 'PAGERDUTY';
```

### Slack Integration

```sql
UPDATE notification_channel_config 
SET enabled = TRUE,
    configuration = '{
      "webhook_url": "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
    }'::jsonb
WHERE channel_name = 'SLACK';
```

## AI/ML Stability Evaluation

The monitoring agent uses multiple algorithms to evaluate service stability:

1. **Statistical Analysis**: Z-score based anomaly detection
2. **Trend Analysis**: Moving average and rate of change
3. **Pattern Recognition**: Identify recurring issues
4. **Predictive Modeling**: Forecast potential failures

The stability evaluation considers:
- Current vs. historical error rates
- Response time trends (5min, 15min, 60min windows)
- Latency variations and outliers
- Throughput anomalies

## Development

### Building

```bash
mvn clean install
```

### Running Tests

```bash
mvn test
```

### Adding a New Notification Channel

1. Update `NotificationService.sendToChannel()` with new channel logic
2. Add configuration to `V35__create_monitoring_agent_tables.sql`
3. Implement the notification sender method

## Troubleshooting

### Agent Not Starting

- Check database connectivity
- Verify Keycloak is running and accessible
- Ensure `agents.monitoring.enabled=true` in application.properties

### No Notifications Received

- Check notification channel configuration in database
- Verify endpoint is registered in `agent_monitoring_config`
- Review logs for notification send failures
- Ensure thresholds are properly configured

### Health Checks Failing

- Verify endpoint URLs are accessible from agent container/host
- Check network connectivity
- Review endpoint health check timeout settings

## Future Enhancements

- [ ] Grafana dashboard integration
- [ ] Machine learning model training on historical data
- [ ] Custom metric collection beyond HTTP health checks
- [ ] Distributed tracing correlation
- [ ] Automatic remediation actions
- [ ] Integration with Kubernetes pod auto-scaling

## License

Copyright © 2024 Sentrius LLC. All rights reserved.
