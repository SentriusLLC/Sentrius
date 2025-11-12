# Enterprise Agent Integration Summary

## Overview

The enterprise agent can now communicate with the Python coding agent to perform automated code generation and PR submission as part of its planning workflow.

## Changes Made

### New Java Service: CodingVerbService

**Location**: `enterprise-agent/src/main/java/io/sentrius/agent/analysis/agents/verbs/CodingVerbService.java`

**Purpose**: Exposes coding operations as AI-callable verbs that the enterprise agent can discover and execute.

**Verbs Provided**:
1. `handleJiraIssueWithCode` - Generate code and create PR for JIRA issue
2. `handleGitHubIssueWithCode` - Generate code and create PR for GitHub issue  
3. `createPullRequest` - Create PR with pre-generated code changes
4. `isCodingAgentAvailable` - Check if coding agent is available

### Configuration

**File**: `enterprise-agent/src/main/resources/chat-helper.properties`

**New Properties**:
```properties
# Coding Agent Configuration
agent.coding.enabled=${CODING_AGENT_ENABLED:false}
agent.coding.callback.url=${CODING_AGENT_URL:http://localhost:8094}
```

### Tests

**File**: `enterprise-agent/src/test/java/io/sentrius/agent/analysis/agents/verbs/CodingVerbServiceTest.java`

**Coverage**: 9 unit tests covering all verb methods and edge cases
- ✅ All tests passing
- Tests availability checks, JIRA handling, GitHub handling, PR creation
- Tests error handling when agent unavailable

### Documentation

**File**: `enterprise-agent/README-CODING-INTEGRATION.md`

**Contents**:
- Architecture overview
- Configuration guide
- Usage examples
- Deployment patterns
- Troubleshooting guide
- Security considerations

## How It Works

### Discovery Process

1. Enterprise agent starts up
2. VerbRegistry scans classpath for @Verb annotated methods
3. CodingVerbService methods are discovered automatically
4. Enterprise agent can now call coding operations via verb registry

### Execution Flow

```
Enterprise Agent Plan
    ↓
Check: isCodingAgentAvailable()
    ↓ (if true)
Execute: handleJiraIssueWithCode(issueKey, repo, context)
    ↓
CodingVerbService sends HTTP request to Python agent
    ↓
Python Coding Agent:
    - Fetches JIRA issue
    - Generates code via LLM
    - Creates PR via GitHub MCP server
    - Updates JIRA with PR link
    ↓
Returns: PR URL and status
    ↓
Enterprise Agent continues with plan
```

### Communication Protocol

- **Protocol**: HTTP/REST
- **Method**: POST to `/execute`
- **Payload**: JSON with task data
- **Response**: Status message with PR URL if successful

## Benefits

1. **Separation of Concerns**: 
   - Enterprise agent: Planning and orchestration
   - Coding agent: Code generation and PR submission

2. **Verb Discovery System**:
   - Automatic capability discovery
   - No hard dependencies between agents
   - Runtime integration

3. **Independent Deployment**:
   - Agents can be scaled independently
   - Different release cycles
   - Language flexibility (Java + Python)

4. **Graceful Degradation**:
   - System works even if coding agent unavailable
   - Enterprise agent checks availability before attempting coding tasks

## Usage Example

```java
// In Enterprise Agent planning logic
VerbRegistry verbRegistry = applicationContext.getBean(VerbRegistry.class);

// Check if coding agent is available
boolean codingAvailable = verbRegistry.execute(
    execution, 
    null, 
    "isCodingAgentAvailable", 
    Map.of()
);

if (codingAvailable) {
    // Include coding task in plan
    Map<String, Object> params = Map.of(
        "issueKey", "PROJECT-123",
        "repository", "owner/repo",
        "context", Map.of(
            "language", "Java",
            "framework", "Spring Boot"
        )
    );
    
    String result = verbRegistry.execute(
        execution,
        null,
        "handleJiraIssueWithCode",
        params
    );
    
    // result contains PR URL if successful
    log.info("PR created: {}", result);
}
```

## Deployment

### Local Development

```bash
# Terminal 1: Start Python Coding Agent
cd python-agent
python3 flask_server.py  # Listens on port 8094

# Terminal 2: Start Enterprise Agent
cd enterprise-agent
export CODING_AGENT_ENABLED=true
export CODING_AGENT_URL=http://localhost:8094
mvn spring-boot:run
```

### Kubernetes

```yaml
# Python Coding Agent
apiVersion: apps/v1
kind: Deployment
metadata:
  name: coding-agent
spec:
  containers:
  - name: coding-agent
    image: sentrius-coding-agent:latest
    ports:
    - containerPort: 8094
---
# Enterprise Agent
apiVersion: apps/v1
kind: Deployment
metadata:
  name: enterprise-agent
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

## Testing

### Build Verification
```
[INFO] BUILD SUCCESS
[INFO] Total time: 02:07 min
```

### Unit Tests
```
CodingVerbServiceTest: 9/9 tests passing ✅
```

## Security

- All operations require proper authentication
- HTTP communication should be secured in production
- Input validation on both sides
- Audit trails via provenance system

## Future Enhancements

1. WebSocket support for real-time updates
2. Retry logic and circuit breakers
3. Performance metrics and monitoring
4. Multi-agent orchestration
5. Advanced error recovery

## Conclusion

The enterprise agent can now seamlessly integrate coding tasks into its automated workflows by communicating with the Python coding agent through the verb discovery system. This enables end-to-end automation from issue identification to PR creation.
