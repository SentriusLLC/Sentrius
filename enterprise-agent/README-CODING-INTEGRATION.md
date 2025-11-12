# Coding Agent Integration with Enterprise Agent

## Overview

The Enterprise Agent can now communicate with the Python Coding Agent to perform automated code generation and PR submission as part of its planning and execution workflow.

## Architecture

```
Enterprise Agent (Java)
├── VerbRegistry - discovers coding verbs
│   └── Scans: "io.sentrius.agent.analysis.agents.verbs"
└── CodingVerbService - provides coding operations as @Verb methods
    ├── handleJiraIssueWithCode(@Verb)
    ├── handleGitHubIssueWithCode(@Verb)
    ├── createPullRequest(@Verb)
    └── isCodingAgentAvailable(@Verb)

Python Coding Agent
├── HTTP endpoint for task execution
└── Handles: JIRA issues, GitHub issues, PR creation
```

## How It Works

1. **Discovery**: The VerbRegistry in the Enterprise Agent automatically discovers the CodingVerbService and its @Verb annotated methods.

2. **Loose Coupling**: The Enterprise Agent communicates with the Python Coding Agent via HTTP, allowing independent deployment and scaling.

3. **Runtime Integration**: When both agents are running, the Enterprise Agent can include coding tasks as part of its automated workflows.

4. **Graceful Degradation**: If the Coding Agent is not available, the Enterprise Agent will log warnings but continue to function with other capabilities.

## Coding Capabilities Available to Enterprise Agents

When the Coding Agent is configured and running, the Enterprise Agent can discover and use these capabilities:

### Handle JIRA Issue with Code
- **Verb**: `handleJiraIssueWithCode`
- **Description**: Generate code and create a PR for a JIRA issue
- **Parameters**: 
  - `issueKey` (String) - JIRA issue key (e.g., "PROJECT-123")
  - `repository` (String) - GitHub repository (format: "owner/repo")
  - `context` (Map) - Additional context (language, framework, etc.)
- **Returns**: Status message with PR URL if successful

### Handle GitHub Issue with Code
- **Verb**: `handleGitHubIssueWithCode`
- **Description**: Generate code and create a PR for a GitHub issue
- **Parameters**:
  - `repository` (String) - GitHub repository (format: "owner/repo")
  - `issueNumber` (Integer) - GitHub issue number
  - `context` (Map) - Additional context (language, framework, etc.)
- **Returns**: Status message with PR URL if successful

### Create Pull Request
- **Verb**: `createPullRequest`
- **Description**: Create a pull request with specified code changes
- **Parameters**:
  - `repository` (String) - GitHub repository (format: "owner/repo")
  - `title` (String) - Pull request title
  - `description` (String) - Pull request description
  - `codeChanges` (Map) - Map containing code changes (files array)
- **Returns**: Status message with PR URL if successful

### Check Coding Agent Availability
- **Verb**: `isCodingAgentAvailable`
- **Description**: Check if coding agent is configured and available
- **Parameters**: None
- **Returns**: Boolean (available/unavailable)

## Configuration

### Enterprise Agent Configuration

Add to `chat-helper.properties` (or your agent's properties file):

```properties
# Coding Agent Configuration
agent.coding.enabled=${CODING_AGENT_ENABLED:false}
agent.coding.callback.url=${CODING_AGENT_URL:http://localhost:8094}
```

### Environment Variables

```bash
# Enable coding agent integration
export CODING_AGENT_ENABLED=true

# Coding agent URL (where Python agent HTTP endpoint is running)
export CODING_AGENT_URL=http://localhost:8094
```

## Example Usage

### From Enterprise Agent Code

```java
// Enterprise Agent discovers coding capabilities
VerbRegistry verbRegistry = applicationContext.getBean(VerbRegistry.class);
verbRegistry.scanClasspath();

// Check if coding agent is available
boolean codingAvailable = verbRegistry.execute(execution, null, "isCodingAgentAvailable", Map.of());

if (codingAvailable) {
    // Handle a JIRA issue with code generation
    Map<String, Object> context = Map.of(
        "language", "Java",
        "framework", "Spring Boot"
    );
    
    String result = verbRegistry.execute(execution, null, "handleJiraIssueWithCode",
        Map.of(
            "issueKey", "PROJECT-123",
            "repository", "owner/repo",
            "context", context
        ));
    
    // Result contains PR URL if successful
    log.info("Coding task result: {}", result);
}
```

### AI Agent Planning Scenario

The Enterprise Agent can include coding tasks in its automated plans:

```
User Request: "Fix the bug in PROJECT-123"

Enterprise Agent Plan:
1. Check if coding agent is available (isCodingAgentAvailable)
2. Fetch JIRA issue details (searchForTickets)
3. Generate code and create PR (handleJiraIssueWithCode)
4. Update JIRA with PR link (automatically done by coding agent)
5. Notify user of completion
```

## Python Coding Agent HTTP Endpoint

The Python Coding Agent needs to expose an HTTP endpoint for the Enterprise Agent to communicate with it.

### Example Flask Endpoint

```python
from flask import Flask, request, jsonify
from agents.coding.coding_agent import CodingAgent
from utils.config_manager import ConfigManager

app = Flask(__name__)

@app.route('/execute', methods=['POST'])
def execute_coding_task():
    """Execute a coding task from the Enterprise Agent."""
    try:
        task_data = request.json
        
        config_manager = ConfigManager('application.properties')
        coding_agent = CodingAgent(config_manager)
        
        result = coding_agent.execute_task(task_data)
        
        return jsonify({
            "status": "success",
            "result": result
        }), 200
        
    except Exception as e:
        return jsonify({
            "status": "error",
            "message": str(e)
        }), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8094)
```

## Deployment

### Local Development

1. **Start Python Coding Agent**:
```bash
cd python-agent
python3 flask_server.py
```

2. **Start Enterprise Agent** with environment variables:
```bash
export CODING_AGENT_ENABLED=true
export CODING_AGENT_URL=http://localhost:8094
cd enterprise-agent
mvn spring-boot:run
```

### Kubernetes Deployment

Both agents can be deployed as separate pods:

```yaml
# Coding Agent Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: coding-agent
spec:
  replicas: 1
  template:
    spec:
      containers:
      - name: coding-agent
        image: sentrius-coding-agent:latest
        ports:
        - containerPort: 8094
---
# Enterprise Agent Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: enterprise-agent
spec:
  replicas: 1
  template:
    spec:
      containers:
      - name: enterprise-agent
        image: sentrius-enterprise-agent:latest
        env:
        - name: CODING_AGENT_ENABLED
          value: "true"
        - name: CODING_AGENT_URL
          value: "http://coding-agent:8094"
```

## Benefits of This Integration

1. **Separation of Concerns**: Enterprise Agent focuses on AI planning, Coding Agent focuses on code generation
2. **Language Flexibility**: Java for enterprise logic, Python for LLM/coding tasks
3. **Independent Deployment**: Agents can be deployed and scaled independently
4. **Discoverable Capabilities**: Enterprise Agent dynamically discovers coding capabilities via Verb system
5. **Graceful Degradation**: System works even if Coding Agent is not available

## Testing

### Unit Tests

Run the Enterprise Agent tests:
```bash
cd enterprise-agent
mvn test -Dtest=CodingVerbServiceTest
```

### Integration Tests

1. Start both agents
2. Use Enterprise Agent to invoke coding tasks
3. Verify PRs are created successfully

## Troubleshooting

### Coding Agent Not Discovered

**Symptom**: `isCodingAgentAvailable()` returns false

**Solutions**:
- Check `CODING_AGENT_ENABLED` is set to `true`
- Verify `CODING_AGENT_URL` is configured correctly
- Ensure Python Coding Agent HTTP endpoint is running

### HTTP Connection Errors

**Symptom**: Enterprise Agent cannot reach Coding Agent

**Solutions**:
- Verify network connectivity between agents
- Check firewall rules
- Ensure Coding Agent is listening on correct port
- For Kubernetes: verify service discovery and DNS

### Task Execution Failures

**Symptom**: Coding Agent returns errors

**Solutions**:
- Check Coding Agent logs for detailed error messages
- Verify GitHub token is configured correctly
- Ensure LLM proxy is accessible
- Check JIRA integration is set up

## Security Considerations

1. **Network Security**: Coding Agent should only be accessible from Enterprise Agent (use network policies)
2. **Authentication**: Consider adding authentication to the HTTP endpoint
3. **Input Validation**: Both agents validate inputs to prevent injection attacks
4. **Audit Trails**: All operations are logged via provenance system

## Future Enhancements

1. **Bidirectional Communication**: Coding Agent can notify Enterprise Agent of status updates
2. **Streaming Results**: Real-time progress updates during code generation
3. **Advanced Error Handling**: Retry logic and circuit breakers
4. **Performance Monitoring**: Metrics for coding task execution times
5. **Multi-Agent Orchestration**: Coordinate multiple coding agents for complex tasks
