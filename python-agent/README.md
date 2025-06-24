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
- **SQLAgent**: Example implementation for SQL operations

## Configuration

### YAML Configuration
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

### Running the SQL Agent
```bash
# With configuration file
python main.py sql_agent --config config.yaml

# With default configuration (uses environment variables)
python main.py sql_agent
```

### Creating Custom Agents
```python
from agents.base import BaseAgent

class MyCustomAgent(BaseAgent):
    def __init__(self, config_path=None):
        super().__init__("My Custom Agent", config_path=config_path)
    
    def execute_task(self):
        # Your custom agent logic here
        self.submit_provenance(
            event_type="CUSTOM_TASK",
            details={"task": "custom_operation"}
        )
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
- `langchain`: LLM integration (for SQL agent)

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