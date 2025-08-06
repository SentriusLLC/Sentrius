# Sentrius Python Agent

This Python agent provides the same APIs and operations as the Java agent, enabling integration with the Sentrius platform for authentication, registration, heartbeat monitoring, and provenance event submission.

## Features

- **Keycloak Integration**: Full authentication support using Keycloak JWT tokens
- **Agent Registration**: Automatic registration with the Sentrius API server
- **Heartbeat Monitoring**: Continuous heartbeat mechanism to maintain connection
- **Provenance Events**: Submit detailed provenance events for audit trails
- **RSA Encryption**: Secure communication using ephemeral RSA keys
- **Configurable**: Support for both YAML configuration files and environment variables
- **Extensible**: Base agent framework for creating custom agents

## Architecture

The Python agent mirrors the Java agent architecture with these key components:

### Services
- **KeycloakService**: Handles authentication and token management
- **AgentClientService**: Manages API communication with Sentrius server
- **EphemeralKeyGen**: RSA key generation and cryptographic operations
- **SentriusAgent**: Main agent framework coordinating all services

### Agent Framework
- **BaseAgent**: Abstract base class for all agents

## Configuration

The Python agent supports multiple configuration approaches, similar to the Java agent:

### Properties-based Configuration (Recommended)

Similar to the Java agent's `application.properties`, you can use a properties file that references agent-specific YAML files:

**application.properties:**
```properties
# Keycloak Configuration
keycloak.realm=sentrius
keycloak.base-url=${KEYCLOAK_BASE_URL:http://localhost:8180}
keycloak.client-id=${KEYCLOAK_CLIENT_ID:python-agents}
keycloak.client-secret=${KEYCLOAK_CLIENT_SECRET:your-client-secret}

# Agent Configuration
agent.name.prefix=python-agent
agent.type=python
agent.callback.url=${AGENT_CALLBACK_URL:http://localhost:8093}
agent.api.url=${AGENT_API_URL:http://localhost:8080/}

# Agent Definitions - these reference YAML files that define agent behavior
agent.chat.helper.config=chat-helper.yaml
agent.chat.helper.enabled=true

agent.data.analyst.config=data-analyst.yaml
agent.data.analyst.enabled=false
```

**chat-helper.yaml:**
```yaml
description: "Agent that handles chat interactions and provides helpful responses via OpenAI integration."
context: |
  You are a helpful agent that is responding to users via a chat interface. Please respond to the user in a friendly and helpful manner.
  Return responses in the following format:
  {
    "previousOperation": "<previousOperation>",
    "nextOperation": "<nextOperation>",
    "summaryForLLM": "<Summary of the terminal thus far for the next LLM assessment>",
    "responseForUser": "<Response to the user>"
  }
```

### Environment Variable Substitution

Properties support environment variable substitution using the `${VARIABLE:default}` syntax:
- `${KEYCLOAK_BASE_URL:http://localhost:8180}` - Uses `KEYCLOAK_BASE_URL` env var or defaults to `http://localhost:8180`
- `${KEYCLOAK_CLIENT_ID:python-agents}` - Uses `KEYCLOAK_CLIENT_ID` env var or defaults to `python-agents`

### Legacy YAML Configuration
```yaml
keycloak:
  server_url: "http://localhost:8080"
  realm: "sentrius"
  client_id: "python-agent"
  client_secret: "your-client-secret"

agent:
  name_prefix: "python-agent"
  agent_type: "python"
  callback_url: "http://localhost:8081"
  api_url: "http://localhost:8080"
  heartbeat_interval: 30

llm:
  enabled: false
  provider: "openai"
  model: "gpt-3.5-turbo"
  api_key: null
  endpoint: null
```

### Environment Variables
```bash
KEYCLOAK_SERVER_URL=http://localhost:8080
KEYCLOAK_REALM=sentrius
KEYCLOAK_CLIENT_ID=python-agent
KEYCLOAK_CLIENT_SECRET=your-client-secret
AGENT_NAME_PREFIX=python-agent
AGENT_API_URL=http://localhost:8080
AGENT_CALLBACK_URL=http://localhost:8081
AGENT_HEARTBEAT_INTERVAL=30
```

## Usage

### Running Agents

#### With Properties Configuration
```bash
# Run chat helper agent using default application.properties
python main.py chat-helper

# Run with custom configuration file
python main.py chat-helper --config my-app.properties

# Run with task data
python main.py chat-helper --task-data '{"message": "Hello, how can you help?"}'

# Test mode (no external services)
TEST_MODE=true python main.py chat-helper
```

#### Environment Variable Configuration
```bash
# Set environment variables
export KEYCLOAK_BASE_URL=http://localhost:8180
export KEYCLOAK_CLIENT_ID=python-agents
export KEYCLOAK_CLIENT_SECRET=your-secret
export TEST_MODE=false

# Run agent
python main.py chat-helper
```

### Agent Framework
The Python agent provides a framework for creating custom agents that integrate with the Sentrius platform. All agents interact through APIs using JWT authentication, working with DTOs from the API and the LLM proxy.

### Creating Custom Agents
```python
from agents.base import BaseAgent

class MyCustomAgent(BaseAgent):
    def __init__(self, config_manager):
        super().__init__(config_manager, name="my-custom-agent")
        # Load agent-specific configuration
        self.agent_definition = config_manager.get_agent_definition('my.custom')
    
    def execute_task(self, task_data=None):
        # Your custom agent logic here
        # Note: All data access is through Sentrius APIs, not direct database connections
        self.submit_provenance(
            event_type="CUSTOM_TASK",
            details={"task": "custom_operation", "data": task_data}
        )
        
        # Return structured response
        return {
            "status": "completed",
            "result": "Custom task executed successfully"
        }
```

**my-custom.yaml:**
```yaml
description: "Custom agent that performs specialized tasks"
context: |
  You are a custom agent designed to handle specific business logic.
  Process requests according to your specialized capabilities.
```

**Add to application.properties:**
```properties
agent.my.custom.config=my-custom.yaml
agent.my.custom.enabled=true
```

### Running Custom Agents
```python
# Add to main.py AVAILABLE_AGENTS dict
from agents.my_custom.my_custom_agent import MyCustomAgent

AVAILABLE_AGENTS = {
    'chat-helper': ChatHelperAgent,
    'my-custom': MyCustomAgent,  # Add your agent here
}
```

```bash
# Run your custom agent
python main.py my-custom --task-data '{"operation": "process_data"}'
```

## API Operations

The Python agent supports all the same API operations as the Java agent:

### Agent Registration
- **Endpoint**: `POST /api/v1/agent/register`
- **Purpose**: Register the agent with the Sentrius API server
- **Authentication**: Keycloak JWT token required

### Heartbeat
- **Endpoint**: `POST /api/v1/agent/heartbeat`
- **Purpose**: Send periodic status updates to maintain connection
- **Frequency**: Configurable (default: 30 seconds)

### Provenance Submission
- **Endpoint**: `POST /api/v1/agent/provenance/submit`
- **Purpose**: Submit detailed provenance events for audit trails
- **Data**: Event type, timestamp, agent ID, and custom details

## Dependencies

- `requests`: HTTP client for API communication
- `PyJWT`: JWT token handling
- `cryptography`: RSA key generation and encryption
- `pyyaml`: YAML configuration parsing

Note: The Python agent accesses data through Sentrius APIs using DTOs and the LLM proxy, not through direct database connections.

## Installation

```bash
pip install -r requirements.txt
```

## Testing

Run the test suite:
```bash
python tests/test_services.py
```

## Security

- Uses ephemeral RSA key pairs for secure communication
- Validates JWT tokens using Keycloak public keys
- Supports encrypted data exchange with the API server
- Maintains secure token management throughout agent lifecycle

## Integration with Java Ecosystem

This Python agent is designed to work seamlessly with the existing Java-based Sentrius infrastructure:

- Compatible with the same API endpoints
- Uses identical authentication mechanisms  
- Submits provenance events in the same format
- Supports the same agent lifecycle management
- Can be launched using the same agent launcher service

## Example Provenance Events

The agent automatically submits various provenance events:

```json
{
  "event_type": "AGENT_REGISTRATION",
  "timestamp": "2024-01-01T12:00:00.000Z",
  "agent_id": "python-agent-abc123",
  "details": {
    "agent_id": "python-agent-abc123",
    "callback_url": "http://localhost:8081",
    "agent_type": "python"
  }
}
```

```json
{
  "event_type": "SQL_QUERY_SUCCESS", 
  "timestamp": "2024-01-01T12:01:00.000Z",
  "agent_id": "python-agent-abc123",
  "details": {
    "question_number": 1,
    "question": "What are the top 5 customers by revenue?",
    "response_length": 245
  }
}
```