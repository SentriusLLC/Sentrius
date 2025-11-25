# SSH Response Agent

## Overview

The SSH Response Agent is a Java-based agent that monitors Kafka queues for SSH user queries and provides intelligent, memory-aware responses. It extends the enterprise-agent base and follows the same pattern as the analytics-agent and monitoring-agent.

## Architecture

This agent is built following the Sentrius agent architecture:
- Extends `enterprise-agent` base package
- Uses Spring Boot with Kafka integration
- Maintains per-user and per-session memory
- Integrates with Sentrius database and authentication

## Key Components

### SshAgent.java
Main Spring Boot application class that:
- Scans base packages for agent and Sentrius services
- Enables JPA repositories
- Enables scheduling
- Registers as a Non-Person Entity (NPE) in the system

### SshQueryConsumerService.java
Kafka consumer service that:
- Listens to `ssh-agent-queries` topic
- Processes incoming SSH user queries
- Delegates to SshResponseService for response generation
- Sends responses to `ssh-agent-responses` topic
- Handles errors gracefully

### SshResponseService.java
Response generation service that:
- Maintains in-memory per-user storage (last 10 queries)
- Maintains in-memory per-session storage (last 20 queries)
- Generates context-aware responses based on query content
- Provides specialized help for common SSH commands

## Memory Management

### Per-User Memory
- Stores up to 10 recent queries per user
- Provides personalized context in responses
- Automatically prunes oldest entries when limit reached

### Per-Session Memory
- Stores up to 20 recent queries per session
- Maintains conversation continuity
- Session-specific context for better assistance

## Configuration

### Application Properties

Located in `src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8095
spring.application.name=sentrius-ssh-agent

# Kafka Configuration
spring.kafka.bootstrap-servers=localhost:9092
ssh.agent.kafka.query.topic=ssh-agent-queries
ssh.agent.kafka.response.topic=ssh-agent-responses
ssh.agent.kafka.consumer.group=ssh-agent-consumer

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/sentrius
spring.datasource.username=postgres
spring.datasource.password=postgres

# Keycloak Configuration
keycloak.realm=sentrius
keycloak.base-url=http://localhost:8180
spring.security.oauth2.client.registration.keycloak.client-id=ssh-agent

# Memory Configuration
agent.memory.enabled=true
agent.memory.max-user-memories=10
agent.memory.max-session-memories=20
```

## Dependencies

From `pom.xml`:

- **enterprise-agent** (1.0-SNAPSHOT) - Base agent framework
- **sentrius-core** - Core Sentrius functionality
- **sentrius-dataplane** - Data layer services
- **spring-kafka** - Kafka integration
- **PostgreSQL** - Database driver
- **Lombok** - Boilerplate reduction
- **JWT libraries** - Authentication

## Running the Agent

### Prerequisites

1. Kafka broker running (default: localhost:9092)
2. PostgreSQL database (default: localhost:5432/sentrius)
3. Keycloak authentication server (default: localhost:8180)
4. Topics created:
   - `ssh-agent-queries`
   - `ssh-agent-responses`

### Starting the Agent

```bash
# From the project root
mvn clean install
cd ssh-agent
mvn spring-boot:run
```

Or with custom configuration:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="\
    --spring.kafka.bootstrap-servers=kafka:9092 \
    --spring.datasource.url=jdbc:postgresql://db:5432/sentrius"
```

### Docker Deployment

Build the jar:
```bash
mvn clean package
```

Run with Java:
```bash
java -jar target/ssh-agent-1.0-SNAPSHOT.jar
```

## Message Flow

```
1. User sends query via SSH session
   ↓
2. SSH Proxy sends SshAgentQueryMessage to Kafka (ssh-agent-queries)
   ↓
3. SSH Agent consumes message
   ↓
4. SshQueryConsumerService processes query
   ↓
5. SshResponseService generates response with memory context
   ↓
6. SshAgentResponseMessage sent to Kafka (ssh-agent-responses)
   ↓
7. SSH Proxy consumes response
   ↓
8. User receives response in SSH session
```

## Message DTOs

### SshAgentQueryMessage
```java
{
  "queryId": "uuid",
  "userId": "user-id",
  "username": "username",
  "userEmail": "user@example.com",
  "sessionId": "session-id",
  "query": "How do I list files?",
  "chatGroupId": "chat-group-id",
  "timestamp": "2024-01-01T00:00:00Z",
  "responseTopic": "ssh-agent-responses"
}
```

### SshAgentResponseMessage
```java
{
  "queryId": "uuid",
  "userId": "user-id",
  "sessionId": "session-id",
  "response": "To list files, use the 'ls' command...",
  "chatGroupId": "chat-group-id",
  "timestamp": "2024-01-01T00:00:01Z",
  "agentId": "ssh-agent",
  "status": "success"
}
```

## Contextual Help

The agent provides specialized assistance for common SSH scenarios:

- **File Listing** (`ls` queries): Explains options like `-l`, `-a`, `-lh`
- **Permissions** (`chmod` queries): Security guidance and examples
- **File Deletion** (`rm` queries): Safety warnings and best practices
- **Elevated Privileges** (`sudo` queries): Policy reminders and audit notice

## Logging

Logs are written to console and can be configured:

```properties
logging.level.io.sentrius.agent.ssh=DEBUG
logging.level.org.springframework.kafka=INFO
```

## Extending the Agent

To add new capabilities:

1. **Add Verb Services** - Create verbs in enterprise-agent pattern
2. **Enhance Response Logic** - Update SshResponseService
3. **Add Memory Storage** - Integrate with PersistentAgentMemoryStore
4. **LLM Integration** - Connect to LLMService for AI-powered responses

## Troubleshooting

### Agent Not Receiving Messages

1. Verify Kafka is running:
   ```bash
   kafka-topics.sh --list --bootstrap-server localhost:9092
   ```

2. Check topic exists:
   ```bash
   kafka-topics.sh --describe --topic ssh-agent-queries --bootstrap-server localhost:9092
   ```

3. Verify consumer group:
   ```bash
   kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group ssh-agent-consumer --describe
   ```

### Database Connection Issues

1. Verify PostgreSQL is running
2. Check credentials in application.properties
3. Ensure database `sentrius` exists

### Build Issues

```bash
# Clean and rebuild
mvn clean install

# Skip tests if needed
mvn clean install -DskipTests
```

## Future Enhancements

- [ ] LLM integration for intelligent responses
- [ ] Persistent memory storage using PersistentAgentMemoryStore
- [ ] Verb-based memory lookup from enterprise-agent
- [ ] Advanced semantic search with VectorAgentMemoryStore
- [ ] Metrics and monitoring dashboards
- [ ] Response caching for common queries
