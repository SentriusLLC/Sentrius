# AI Support Agent

## Overview

The AI Support Agent is a pluggable security feature that provides intelligent, context-aware assistance to users during SSH and web terminal sessions. It monitors commands, offers proactive suggestions, searches documentation and TSGs, and helps users understand potentially dangerous or complex operations.

## Features

### 🤖 Proactive Assistance
- Monitors commands for complexity or danger
- Offers suggestions before execution
- Helps prevent mistakes and security issues
- **Detects common command mistakes in real-time**
- **Analyzes command history for context-aware suggestions**

### 🔍 Intelligent Mistake Detection
- Detects `chown` used with numeric permissions (should be `chmod`)
- Detects `chmod` used with user:group format (should be `chown`)
- Identifies workflow-based issues (e.g., permission changes after file creation)
- Provides immediate, actionable feedback

### 📊 Context-Aware Analysis
- Maintains command history buffer (configurable size)
- Analyzes patterns across multiple commands
- Summarizes recent activity for LLM analysis
- Detects file operations followed by permission changes

### 📚 Documentation Integration (RAG)
- **Retrieval-Augmented Generation** approach
- Searches TSGs (Troubleshooting Guides) with semantic search
- Finds relevant documentation based on command context
- Injects document summaries into LLM prompts for informed responses
- Both rule layer (proactive) and web layer (on-demand) use RAG

### 💬 Interactive Dialog
- Supports @agent and /ask commands
- Natural language queries
- Chat-based interaction in web terminal

### 🔌 Pluggable Architecture
- Configurable like DeletePrevention and TwoPartyAIMonitor
- Can be enabled/disabled per host group
- Customizable thresholds and behavior

## Architecture

### Components

1. **AISupportAgent** (`llm-dataplane` module)
   - Extends `SessionTokenEvaluator`
   - Implements command analysis and triggering logic
   - **Proactive RAG**: Searches docs for command context, enriches LLM prompts
   - Configurable via rule configuration string

2. **AISupportLLMService** (`llm-dataplane` module)
   - Pluggable service for rule layer LLM calls
   - Uses GenerativeAPI with enhanced prompts including document context
   - Generates suggestions informed by retrieved documentation

3. **WebTerminalAISupportService** (`api` module)
   - Service layer for web terminal integration
   - **On-demand RAG**: Searches docs for user queries
   - Handles document search and response generation
   - Manages agent execution context and chat logging

4. **TerminalWSHandler** (modified)
   - Detects @agent commands in web terminal
   - Routes queries to AI Support Service
   - Sends responses via chat websocket

5. **SshAgentInteractionService** (existing)
   - Already handles @agent in SSH proxy
   - Kafka-based communication with ssh-agent
   - Used by both SSH proxy and web terminal

## Usage

### In Web Terminal

Users can interact with the AI Support Agent in two ways:

1. **Explicit Queries**:
   ```bash
   @agent How do I list all files recursively?
   /ask What is the chmod command?
   ```

2. **Proactive Suggestions**:
   When typing dangerous or complex commands, the agent may prompt:
   ```bash
   $ rm -rf /important/data
   ⚠️ This command is dangerous. Would you like to see safer alternatives?
   ```

3. **Intelligent Mistake Detection**:
   The agent immediately catches common mistakes:
   ```bash
   $ chown 755 myfile.txt
   ⚠️ Potential command mistake detected
   
   Did you mean `chmod` instead of `chown`?
   
   • `chown` changes file ownership (e.g., chown user:group file)
   • `chmod` changes file permissions (e.g., chmod 755 file)
   
   Your command appears to use numeric permissions with chown, 
   which typically indicates you meant chmod.
   ```

4. **Context-Aware Suggestions**:
   The agent analyzes your command history:
   ```bash
   $ touch newfile.txt
   $ echo "content" > newfile.txt
   $ chown 755 newfile.txt
   
   💡 AI Assistant noticed your recent activity
   
   You recently created files and are now changing permissions.
   However, `chown 755` should be `chmod 755` for setting permissions.
   Use `chown user:group` to change ownership instead.
   ```

### In SSH Proxy

The same @agent commands work in SSH proxy sessions:
```bash
$ @agent Help me understand this error
$ /ask What does this command do?
```

### Response Format

Agent responses include:
- **Contextual Help**: Explanations and tips
- **Documentation Links**: Relevant TSGs and docs (top 3 by default)
- **Examples**: Common usage patterns
- **Warnings**: Security and safety notices

Example response:
```
To list files and directories:
• ls - List files in current directory
• ls -la - List all files with details
• ls -lh - List with human-readable file sizes
• find . -name "pattern" - Search for files by name

📚 Relevant Documentation:
1. Basic Commands Guide (TSG)
   Introduction to common Linux commands

2. File Management Best Practices (MANUAL)
   Guidelines for file operations

You can access these documents through the documentation portal.
```

## Configuration

### As a Pluggable Rule

Administrators can configure the AI Support Agent through the web UI:

1. Navigate to **Rules** → **Configure Pluggable Rule**
2. Select **AI Support Agent**
3. Configure settings:

**Configuration String Format**:
```
enabled=true;proactiveMode=true;bufferSize=5;threshold=0.7
```

**Parameters**:
- `enabled` (boolean): Enable/disable the agent (default: true)
- `proactiveMode` (boolean): Offer proactive suggestions (default: true)
- `bufferSize` (integer): Number of recent commands to buffer for context analysis (default: 5)
  - Higher values provide more context for pattern detection
  - Recommended: 5-10 for optimal mistake detection
- `threshold` (double): Similarity threshold for document search (default: 0.7)

### Application Properties

```properties
# Enable AI Support Agent
agent.support.enabled=true

# Include document search in responses
agent.support.include.documents=true

# Maximum documents to show in response
agent.support.max.docs=3
```

## Command Detection

The AI Support Agent uses multiple layers of detection:

### Immediate Mistake Detection
- **`chown` with numeric permissions** (e.g., `chown 755 file`) → Should be `chmod`
- **`chmod` with user:group format** (e.g., `chmod user:group file`) → Should be `chown`
- Detected instantly without LLM call for fast feedback

### Context-Based Analysis
- **File operations followed by permission changes**
  - Tracks file creation (`touch`, `echo >`, `cat >`, `vim`, etc.)
  - Monitors permission commands (`chmod`, `chown`, `chgrp`)
  - Analyzes patterns to detect workflow issues
- **Command history summarization**
  - Maintains buffer of recent commands
  - Provides context to LLM for intelligent suggestions
  - Detects related actions across multiple commands

### Dangerous Commands
- `rm -rf` operations
- `dd if=` disk operations
- File system formatting (`mkfs`, `fdisk`)
- System modifications

### Complex Commands
- `find` with multiple options
- `grep`, `awk`, `sed` patterns
- Long command chains (>100 chars)
- Multiple pipes (>3 pipes)
- Container operations (`docker`, `kubectl`)

## Document Search (RAG Implementation)

The AI Support Agent uses a **Retrieval-Augmented Generation (RAG)** approach in both layers:

### Rule Layer (Proactive RAG)
When commands trigger proactive suggestions:
1. **Command analysis**: Detects dangerous/complex commands
2. **Document retrieval**: Searches TSGs and docs based on command context
3. **Context enrichment**: Injects top 3 document summaries into LLM prompt
4. **Informed generation**: LLM generates suggestions with document knowledge
5. **User prompt**: Sends suggestion via PROMPT_ACTION trigger

### Web Layer (On-Demand RAG)
When users query with `@agent` or `/ask`:
1. **Query processing**: User submits natural language query
2. **Document retrieval**: Searches TSGs first, then all docs
3. **Context building**: Extracts summaries from top matches
4. **LLM augmentation**: Builds prompt with query + document context
5. **Response generation**: LLM produces answer informed by docs
6. **Document links**: Includes references to source documents in response

### Search Strategy
1. **Searches TSGs first**: Prioritizes troubleshooting guides
2. **Falls back to all docs**: If no TSGs match
3. **Applies access control**: Respects user permissions
4. **Uses semantic search**: When available (vector embeddings)
5. **Returns top matches**: Top 3-5 for context, configurable

### Supported Document Types
- TSG (Troubleshooting Guides)
- MANUAL (User Manuals)
- POLICY (Security Policies)
- RUNBOOK (Operational Runbooks)
- EXTERNAL (Retrieved Documents)

## Security

### Access Control
- Documents respect user attributes and markings
- Users only see docs they're authorized to access
- All interactions are logged for audit

### Privacy
- All queries and responses logged in ChatLog
- Session tracking for context
- No sensitive data stored in agent memory

### Integration Points
- Uses existing ChatService for logging
- Integrates with AgentExecutionService
- Respects DocumentAccessControlService policies

## Testing

### Unit Tests

Run the test suite:
```bash
cd llm-dataplane
mvn test -Dtest=AISupportAgentTest
```

### Manual Testing

1. **Start Sentrius**:
   ```bash
   ./ops-scripts/local/run-sentrius.sh --build
   ```

2. **Configure the rule**:
   - Log into web UI
   - Navigate to Rules → Pluggable Rules
   - Add AI Support Agent rule with configuration

3. **Test in web terminal**:
   - Open a terminal session
   - Type `@agent help` to test
   - Try dangerous commands to see proactive suggestions

4. **Test document search**:
   - Upload some TSGs via document management
   - Query: `@agent how do I troubleshoot network issues?`
   - Verify relevant docs appear in response

## Troubleshooting

### Agent Not Responding

1. **Check configuration**:
   ```bash
   # Verify rule is enabled in database
   psql -d sentrius -c "SELECT * FROM rules WHERE rule_class LIKE '%AISupportAgent%';"
   ```

2. **Check logs**:
   ```bash
   grep "AI Support Agent" /var/log/sentrius/api.log
   ```

3. **Verify WebSocket connection**:
   - Open browser dev tools
   - Check WebSocket connection to `/api/v1/chat/attach/subscribe`

### Documents Not Appearing

1. **Check DocumentService availability**:
   - DocumentService may be optional autowired
   - Verify it's configured and running

2. **Verify user permissions**:
   - User must have attributes matching document markings
   - Check AccessEvaluator for user

3. **Check document uploads**:
   - Ensure documents are uploaded via UI
   - Verify document types and tags

### Proactive Mode Not Working

1. **Verify configuration**:
   - Check `proactiveMode=true` in rule config
   - Ensure `enabled=true`

2. **Check command patterns**:
   - Review AISupportAgent.java patterns
   - Some simple commands intentionally don't trigger

## Future Enhancements

- [ ] LLM integration for intelligent response generation
- [ ] Learning from user feedback
- [ ] Command explanation and safety analysis
- [ ] Integration with external knowledge bases
- [ ] Multi-language support
- [ ] Voice/audio query support
- [ ] Enhanced context awareness with session history

## Related Documentation

- [SSH Proxy README](../../ssh-proxy/README.md) - @agent in SSH sessions
- [SSH Agent README](../../ssh-agent/README.md) - Kafka-based agent service
- [Document Service](../../dataplane/src/main/java/io/sentrius/sso/core/services/documents/README.md)
- [Pluggable Rules](../../docs/PLUGGABLE_RULES.md)

## License

This feature is part of the Sentrius zero-trust security platform.
