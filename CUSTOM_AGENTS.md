# Custom Agents

Sentrius supports both Java and Python-based custom agents that can extend the platform's functionality for monitoring, automation, and user assistance.

## Table of Contents

- [Overview](#overview)
- [Java Agents](#java-agents)
- [Python Agents](#python-agents)
- [Agent Development Best Practices](#agent-development-best-practices)

## Overview

Custom agents in Sentrius can:
- Monitor SSH sessions and system activity
- Provide user assistance and automation
- Integrate with external services via zero trust access
- Execute custom business logic
- Submit provenance events for audit trails

## Java Agents

Java agents are built using the Spring Boot framework and integrate with the Sentrius ecosystem through the agent launcher service.

### Creating a Custom Java Agent

#### 1. Create Module Structure

```
my-custom-agent/
├── src/main/java/
│   └── io/sentrius/agent/mycustom/
│       ├── MyCustomAgent.java
│       └── MyCustomAgentConfig.java
└── pom.xml
```

#### 2. Implement the Agent Interface

```java
@Component
@ConditionalOnProperty(name = "agents.mycustom.enabled", havingValue = "true")
public class MyCustomAgent implements ApplicationListener<ApplicationReadyEvent> {
    
    @Autowired
    private AgentService agentService;
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Register agent and start processing
        agentService.register(this);
    }
    
    @Scheduled(fixedDelay = 60000)  // Run every minute
    public void processTask() {
        // Your agent logic here
        logger.info("Processing custom agent task");
    }
}
```

#### 3. Configuration Properties

```java
@ConfigurationProperties(prefix = "agents.mycustom")
@Data
public class MyCustomAgentConfig {
    private boolean enabled = false;
    private String name = "my-custom-agent";
    private String description = "Custom agent for specialized tasks";
    private int pollInterval = 60000;
}
```

#### 4. Add to application.properties

```properties
agents.mycustom.enabled=true
agents.mycustom.name=my-custom-agent
agents.mycustom.description=Custom agent for specialized tasks
agents.mycustom.pollInterval=60000
```

#### 5. Deploy with Helm Chart

Add to `values.yaml`:

```yaml
mycustomagent:
  image:
    repository: my-custom-agent
    tag: latest
  oauth2:
    client_id: java-agents
    client_secret: your-secret
  resources:
    requests:
      memory: "256Mi"
      cpu: "100m"
    limits:
      memory: "512Mi"
      cpu: "500m"
```

### Java Agent Features

- **Zero Trust Integration**: Automatic ZTAT (Zero Trust Access Token) handling
- **Provenance Tracking**: Built-in event logging and audit trails
- **LLM Integration**: Access to language models through the LLM proxy
- **Session Monitoring**: Real-time SSH session monitoring capabilities
- **RESTful APIs**: Full access to Sentrius APIs and data

### Example: Session Monitoring Agent

```java
@Component
public class SessionMonitorAgent implements ApplicationListener<ApplicationReadyEvent> {
    
    @Autowired
    private SshSessionService sessionService;
    
    @Autowired
    private ProvenanceService provenanceService;
    
    @Scheduled(fixedDelay = 30000)
    public void monitorSessions() {
        List<SshSession> activeSessions = sessionService.getActiveSessions();
        
        for (SshSession session : activeSessions) {
            if (isAnomalous(session)) {
                provenanceService.submit(ProvenanceEvent.builder()
                    .eventType("ANOMALOUS_SESSION_DETECTED")
                    .sessionId(session.getId())
                    .details(Map.of(
                        "user", session.getUsername(),
                        "reason", "Suspicious command pattern detected"
                    ))
                    .build());
                
                // Take action
                sessionService.flagSession(session.getId());
            }
        }
    }
    
    private boolean isAnomalous(SshSession session) {
        // Your anomaly detection logic
        return false;
    }
}
```

## Python Agents

Python agents provide a flexible framework for creating custom automation and user assistance tools.

### Creating a Custom Python Agent

#### 1. Set up the Agent Structure

```python
# agents/my_custom/my_custom_agent.py
from agents.base import BaseAgent

class MyCustomAgent(BaseAgent):
    def __init__(self, config_manager):
        super().__init__(config_manager, name="my-custom-agent")
        self.agent_definition = config_manager.get_agent_definition('my.custom')
    
    def execute_task(self, task_data=None):
        """Execute the agent's main task"""
        self.logger.info(f"Executing custom task with data: {task_data}")
        
        # Your custom logic here
        result = self.process_data(task_data)
        
        # Submit provenance event
        self.submit_provenance(
            event_type="CUSTOM_TASK",
            details={
                "task": "custom_operation",
                "data": task_data,
                "result": result
            }
        )
        
        return {
            "status": "completed",
            "result": result
        }
    
    def process_data(self, data):
        """Process the task data"""
        # Implement your logic
        return "processed_result"
```

#### 2. Create Agent Configuration

Create `my-custom.yaml`:

```yaml
description: "Custom agent that performs specialized tasks"
context: |
  You are a custom agent designed to handle specific business logic.
  Process requests according to your specialized capabilities.
  
  Your responsibilities include:
  - Processing custom data
  - Submitting provenance events
  - Integrating with external services
```

#### 3. Add to application.properties

```properties
agent.my.custom.config=my-custom.yaml
agent.my.custom.enabled=true
agent.my.custom.poll.interval=60000
```

#### 4. Register in main.py

```python
from agents.my_custom.my_custom_agent import MyCustomAgent

AVAILABLE_AGENTS = {
    'chat-helper': ChatHelperAgent,
    'my-custom': MyCustomAgent,  # Add your agent here
    'mcp': MCPAgent,
}
```

#### 5. Run Your Custom Agent

```bash
# Test mode (no external services)
TEST_MODE=true python main.py my-custom --task-data '{"operation": "process_data"}'

# With properties configuration
python main.py my-custom --config my-app.properties

# With environment variables
export KEYCLOAK_BASE_URL=http://localhost:8180
export KEYCLOAK_CLIENT_ID=python-agents
python main.py my-custom
```

### Python Agent Features

- **API Integration**: Full access to Sentrius APIs using JWT authentication
- **Configuration Management**: Support for properties files and YAML configurations
- **LLM Proxy Access**: Integration with language models for AI-powered tasks
- **Provenance Submission**: Automatic event tracking and audit logging
- **Keycloak Authentication**: Built-in OAuth2/JWT token management

### Example: Data Processing Agent

```python
from agents.base import BaseAgent
import requests

class DataProcessingAgent(BaseAgent):
    def __init__(self, config_manager):
        super().__init__(config_manager, name="data-processor")
        self.api_endpoint = config_manager.get_property('api.endpoint')
    
    def execute_task(self, task_data=None):
        """Process data from external sources"""
        
        # Fetch data from API
        headers = self.get_auth_headers()
        response = requests.get(
            f"{self.api_endpoint}/data",
            headers=headers
        )
        
        if response.status_code == 200:
            data = response.json()
            processed = self.process(data)
            
            # Submit results
            self.submit_results(processed)
            
            # Track in provenance
            self.submit_provenance(
                event_type="DATA_PROCESSED",
                details={
                    "records": len(processed),
                    "status": "success"
                }
            )
            
            return {"status": "completed", "records": len(processed)}
        else:
            self.logger.error(f"Failed to fetch data: {response.status_code}")
            return {"status": "failed", "error": response.text}
    
    def process(self, data):
        """Process the data"""
        # Your processing logic
        return [item for item in data if self.is_valid(item)]
    
    def is_valid(self, item):
        """Validate data item"""
        return item.get('status') == 'active'
    
    def submit_results(self, processed_data):
        """Submit processed data back to API"""
        headers = self.get_auth_headers()
        requests.post(
            f"{self.api_endpoint}/results",
            headers=headers,
            json=processed_data
        )
```

## Agent Development Best Practices

### 1. Authentication

Always use proper OAuth2/JWT authentication:

**Java:**
```java
@Autowired
private OAuth2ClientService oauth2Client;

public String getAccessToken() {
    return oauth2Client.getAccessToken("java-agents");
}
```

**Python:**
```python
def get_auth_headers(self):
    token = self.auth_manager.get_access_token()
    return {
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json'
    }
```

### 2. Provenance Tracking

Submit detailed provenance events for audit trails:

**Java:**
```java
provenanceService.submit(ProvenanceEvent.builder()
    .eventType("AGENT_ACTION")
    .agentName("my-agent")
    .action("process_data")
    .details(Map.of(
        "records_processed", count,
        "duration_ms", duration
    ))
    .build());
```

**Python:**
```python
self.submit_provenance(
    event_type="AGENT_ACTION",
    details={
        "action": "process_data",
        "records_processed": count,
        "duration_ms": duration
    }
)
```

### 3. Error Handling

Implement robust error handling and logging:

**Java:**
```java
try {
    processData();
} catch (Exception e) {
    logger.error("Failed to process data", e);
    provenanceService.submit(ProvenanceEvent.builder()
        .eventType("AGENT_ERROR")
        .error(e.getMessage())
        .build());
    throw new AgentException("Processing failed", e);
}
```

**Python:**
```python
try:
    self.process_data()
except Exception as e:
    self.logger.error(f"Failed to process data: {e}")
    self.submit_provenance(
        event_type="AGENT_ERROR",
        details={"error": str(e)}
    )
    raise
```

### 4. Configuration Management

Use environment-specific configurations:

**Java:**
```java
@ConfigurationProperties(prefix = "agents.mycustom")
public class MyAgentConfig {
    private String apiEndpoint;
    private int timeout = 30000;
    private boolean enableRetry = true;
    // Getters and setters
}
```

**Python:**
```python
class MyAgentConfig:
    def __init__(self, config_manager):
        self.api_endpoint = config_manager.get_property('api.endpoint')
        self.timeout = int(config_manager.get_property('api.timeout', '30'))
        self.enable_retry = config_manager.get_property('api.retry', 'true') == 'true'
```

### 5. Testing

Test agents in isolation before integration:

**Java:**
```java
@SpringBootTest
public class MyCustomAgentTest {
    @Autowired
    private MyCustomAgent agent;
    
    @Test
    public void testProcessTask() {
        // Arrange
        TaskData data = new TaskData();
        
        // Act
        Result result = agent.processTask(data);
        
        // Assert
        assertNotNull(result);
        assertEquals("completed", result.getStatus());
    }
}
```

**Python:**
```bash
# Test mode (no external services)
TEST_MODE=true python main.py my-custom --task-data '{"test": true}'

# Unit tests
python -m pytest tests/test_my_custom_agent.py
```

### 6. Resource Management

Be mindful of resource usage:

**Java:**
```yaml
mycustomagent:
  resources:
    requests:
      memory: "256Mi"
      cpu: "100m"
    limits:
      memory: "512Mi"
      cpu: "500m"
```

**Python:**
- Use connection pooling for database connections
- Close resources properly in finally blocks
- Implement timeouts for external API calls

### 7. Documentation

Document agent capabilities and configuration:

```markdown
# My Custom Agent

## Purpose
Brief description of what the agent does.

## Configuration
List of configuration properties and their defaults.

## API Endpoints
List of API endpoints the agent uses.

## Provenance Events
List of events the agent submits.

## Dependencies
External services or libraries required.
```

## Advanced Topics

### LLM Integration

Agents can leverage language models for AI-powered functionality:

**Java:**
```java
@Autowired
private LLMProxyService llmProxy;

public String analyzeText(String text) {
    LLMRequest request = LLMRequest.builder()
        .prompt("Analyze the following text: " + text)
        .maxTokens(500)
        .build();
    
    return llmProxy.complete(request).getContent();
}
```

**Python:**
```python
def analyze_text(self, text):
    response = self.llm_client.complete(
        prompt=f"Analyze the following text: {text}",
        max_tokens=500
    )
    return response['content']
```

### Dynamic Agent Deployment

Use the agent-launcher service for dynamic deployment:

```bash
curl -X POST http://agent-launcher:8080/api/v1/agents/launch \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "agentType": "my-custom-agent",
    "configuration": {
      "task": "process_data",
      "schedule": "0 */5 * * *"
    }
  }'
```

### Session Interception

Agents can intercept and monitor SSH sessions:

```java
@Component
public class SessionInterceptor implements SshSessionListener {
    
    @Override
    public void onCommand(SshSession session, String command) {
        if (isDangerous(command)) {
            session.block();
            notifyAdmin(session, command);
        }
    }
    
    private boolean isDangerous(String command) {
        return command.contains("rm -rf") || command.contains("dd if=");
    }
}
```

## Next Steps

- Review [DEVELOPMENT.md](DEVELOPMENT.md) for development workflows
- See [DEPLOYMENT.md](DEPLOYMENT.md) for deployment options
- Check [INTEGRATIONS.md](INTEGRATIONS.md) for external service integrations
- Read [python-agent/README.md](python-agent/README.md) for Python agent specifics
