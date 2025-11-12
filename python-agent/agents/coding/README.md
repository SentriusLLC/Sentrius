# Coding Agent

The Coding Agent is an automated code generation and PR submission agent that integrates with GitHub and JIRA to handle coding tasks.

## Overview

The Coding Agent can:
- Accept coding tasks from JIRA issues or GitHub issues
- Generate code using LLM integration
- Create pull requests with the generated code
- Update JIRA issues with PR links
- Track provenance for all operations

## Architecture

The agent integrates with:
- **GitHub MCP Server**: For repository operations (branch creation, commits, PRs)
- **JIRA Proxy**: For fetching issues and adding comments
- **LLM Proxy**: For AI-powered code generation
- **Sentrius API**: For authentication and provenance tracking

## Configuration

### application.properties

```properties
# Enable the coding agent
agent.coding.config=python-agent/coding.yaml
agent.coding.enabled=true
```

### coding.yaml

```yaml
description: "Agent that handles automated code generation and pull request submission"
integration_proxy_url: "${INTEGRATION_PROXY_URL:http://localhost:8080}"
llm_proxy_url: "${LLM_PROXY_URL:http://localhost:8080}"
github_token_id: "${GITHUB_TOKEN_ID:1}"  # IntegrationSecurityToken ID for GitHub
llm_model: "${LLM_MODEL:gpt-4}"
```

### Environment Variables

- `INTEGRATION_PROXY_URL`: URL of the integration proxy service
- `LLM_PROXY_URL`: URL of the LLM proxy service
- `GITHUB_TOKEN_ID`: ID of the GitHub IntegrationSecurityToken
- `LLM_MODEL`: LLM model to use for code generation (default: gpt-4)

## Usage

### Handle JIRA Issue

```bash
python main.py coding --task-data '{
  "operation": "handle_jira_issue",
  "issue_key": "PROJECT-123",
  "repo": "owner/repository",
  "context": {
    "language": "Python",
    "framework": "Flask"
  }
}'
```

### Handle GitHub Issue

```bash
python main.py coding --task-data '{
  "operation": "handle_github_issue",
  "repo": "owner/repository",
  "issue_number": 456,
  "context": {
    "language": "Java",
    "framework": "Spring Boot"
  }
}'
```

### Create Pull Request Directly

```bash
python main.py coding --task-data '{
  "operation": "create_pr",
  "repo": "owner/repository",
  "title": "Add new feature",
  "description": "Implementation of feature X",
  "code_changes": {
    "files": [
      {
        "path": "src/main/java/Example.java",
        "content": "public class Example { ... }",
        "operation": "create"
      }
    ]
  }
}'
```

## Task Data Format

### Common Fields

- `operation`: Type of operation to perform
  - `handle_jira_issue`: Process a JIRA issue
  - `handle_github_issue`: Process a GitHub issue
  - `create_pr`: Create a pull request directly

### JIRA Issue Fields

- `issue_key`: JIRA issue key (e.g., "PROJECT-123")
- `repo`: GitHub repository (format: "owner/repo")
- `context`: Additional context for code generation (optional)

### GitHub Issue Fields

- `repo`: GitHub repository (format: "owner/repo")
- `issue_number`: GitHub issue number
- `context`: Additional context for code generation (optional)

### Direct PR Fields

- `repo`: GitHub repository (format: "owner/repo")
- `title`: Pull request title
- `description`: Pull request description
- `code_changes`: Pre-generated code changes (optional)
  - `files`: Array of file objects
    - `path`: File path
    - `content`: File content
    - `operation`: "create", "update", or "delete"

## Workflow

### JIRA Issue Workflow

1. Fetch issue details from JIRA
2. Extract task requirements (summary, description)
3. Generate code using LLM
4. Create PR on GitHub
5. Update JIRA issue with PR link

### GitHub Issue Workflow

1. Fetch issue details from GitHub via MCP server
2. Extract task requirements (title, body)
3. Generate code using LLM
4. Create PR on GitHub

### PR Creation Workflow

1. Ensure GitHub MCP server is running
2. Create a new branch (format: `automated/{sanitized-title}`)
3. Commit changes to the branch
4. Create pull request from branch to main

## Code Generation

The agent uses LLM integration to generate code based on:
- Task title and description
- Additional context (language, framework, etc.)
- Agent's system prompt (coding best practices)

The LLM returns structured code changes in JSON format:

```json
{
  "files": [
    {
      "path": "path/to/file",
      "content": "file content",
      "operation": "create|update|delete"
    }
  ],
  "explanation": "Brief explanation of changes"
}
```

## Security

- All operations require Keycloak JWT authentication
- GitHub operations go through zero-trust MCP server
- JIRA operations go through authenticated proxy
- All provenance events are tracked

## Prerequisites

1. **GitHub Integration**:
   - GitHub Personal Access Token stored as IntegrationSecurityToken
   - GitHub MCP server container available in Kubernetes

2. **JIRA Integration**:
   - JIRA integration configured in Sentrius
   - JIRA credentials stored securely

3. **LLM Access**:
   - LLM proxy service running
   - Appropriate LLM model configured

4. **Kubernetes Cluster**:
   - For GitHub MCP server deployment
   - With appropriate RBAC permissions

## Error Handling

The agent handles errors at each step:
- **JIRA fetch failures**: Logs error and raises exception
- **GitHub MCP server unavailable**: Launches server automatically
- **LLM generation failures**: Logs error and raises exception
- **PR creation failures**: Returns error details

All errors are tracked via provenance events.

## Provenance Events

The agent submits provenance events for:
- `CODING_TASK_START`: Task execution starts
- `CODING_TASK_COMPLETE`: Task execution completes
- `CODING_TASK_ERROR`: Task execution fails

## Testing

### Test Mode

Run in test mode to verify configuration without external services:

```bash
TEST_MODE=true python main.py coding --task-data '{...}'
```

### Integration Testing

For full integration testing, ensure all services are available:
- Keycloak running on configured URL
- Integration proxy running
- LLM proxy running
- Kubernetes cluster accessible

## Future Enhancements

- Support for multiple GitHub repositories
- Automated testing of generated code
- Code review integration
- Multi-file change optimization
- Branch cleanup after PR merge
- Advanced conflict resolution
- Support for GitLab and Bitbucket
