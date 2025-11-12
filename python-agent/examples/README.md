# Coding Agent Examples

This directory contains example scripts and workflows for integrating the Coding Agent with various automation tools.

## Overview

The Coding Agent can be integrated with:
- JIRA webhooks
- GitHub webhooks
- CI/CD pipelines
- Cron jobs
- Custom automation tools

## Examples

### 1. JIRA Webhook Handler

`jira-webhook-handler.sh` - Shell script for handling JIRA webhook events.

**Usage:**
```bash
./jira-webhook-handler.sh PROJECT-123 owner/repository
```

**Integration with JIRA:**
1. Configure JIRA webhook to call this script
2. Set up webhook to trigger on issue creation or updates
3. Script automatically invokes coding agent

### 2. GitHub Webhook Handler

`github-webhook-handler.sh` - Shell script for handling GitHub webhook events.

**Usage:**
```bash
./github-webhook-handler.sh owner/repository 456
```

**Integration with GitHub:**
1. Configure GitHub webhook to call this script
2. Set up webhook to trigger on issue creation or labels
3. Script automatically invokes coding agent

### 3. Python Automation

`automation_example.py` - Python wrapper for automated workflows.

**Usage:**
```python
from automation_example import CodingAgentAutomation

automation = CodingAgentAutomation()

# Handle JIRA issue
result = automation.handle_jira_issue(
    issue_key="PROJECT-123",
    repo="owner/repository",
    context={"language": "Python"}
)
```

## CI/CD Integration

### GitHub Actions

```yaml
name: Auto Code Generation
on:
  issues:
    types: [opened, labeled]

jobs:
  generate-code:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Coding Agent
        env:
          KEYCLOAK_BASE_URL: ${{ secrets.KEYCLOAK_URL }}
          KEYCLOAK_CLIENT_SECRET: ${{ secrets.KEYCLOAK_SECRET }}
          GITHUB_TOKEN_ID: ${{ secrets.GITHUB_TOKEN_ID }}
        run: |
          cd python-agent
          python3 main.py coding --task-data '{
            "operation": "handle_github_issue",
            "repo": "${{ github.repository }}",
            "issue_number": ${{ github.event.issue.number }}
          }'
```

### Jenkins Pipeline

```groovy
pipeline {
    agent any
    
    environment {
        KEYCLOAK_BASE_URL = credentials('keycloak-url')
        KEYCLOAK_CLIENT_SECRET = credentials('keycloak-secret')
        GITHUB_TOKEN_ID = credentials('github-token-id')
    }
    
    stages {
        stage('Generate Code') {
            steps {
                script {
                    def taskData = """
                    {
                        "operation": "handle_jira_issue",
                        "issue_key": "${params.JIRA_ISSUE}",
                        "repo": "${params.GITHUB_REPO}"
                    }
                    """
                    
                    sh """
                        cd python-agent
                        python3 main.py coding --task-data '${taskData}'
                    """
                }
            }
        }
    }
}
```

### GitLab CI

```yaml
generate-code:
  image: python:3.12
  script:
    - cd python-agent
    - pip install -r requirements.txt
    - |
      python3 main.py coding --task-data '{
        "operation": "handle_github_issue",
        "repo": "'${GITHUB_REPO}'",
        "issue_number": '${GITHUB_ISSUE_NUMBER}'
      }'
  only:
    - triggers
```

## Automation Patterns

### 1. Issue Label Trigger

Automatically generate code when specific labels are added:

```bash
# GitHub webhook payload parsing
LABEL=$(echo "$WEBHOOK_PAYLOAD" | jq -r '.label.name')

if [ "$LABEL" == "auto-code" ]; then
    ./github-webhook-handler.sh "$REPO" "$ISSUE_NUMBER"
fi
```

### 2. Scheduled Automation

Use cron to process JIRA issues periodically:

```cron
# Run every hour to check for new coding tasks
0 * * * * /path/to/jira-webhook-handler.sh PROJECT-* owner/repo
```

### 3. Slack Integration

Integrate with Slack commands:

```python
@app.route('/slack/command', methods=['POST'])
def handle_slack_command():
    data = request.form
    command = data.get('text')
    
    # Parse: /generate-code PROJECT-123 owner/repo
    parts = command.split()
    issue_key = parts[0]
    repo = parts[1]
    
    automation = CodingAgentAutomation()
    result = automation.handle_jira_issue(issue_key, repo)
    
    return jsonify({
        "text": f"PR created: {result.get('pr_url')}"
    })
```

## Environment Configuration

All examples require these environment variables:

```bash
# Keycloak Configuration
export KEYCLOAK_BASE_URL=http://localhost:8180
export KEYCLOAK_CLIENT_ID=python-agents
export KEYCLOAK_CLIENT_SECRET=your-secret

# Integration Configuration
export INTEGRATION_PROXY_URL=http://localhost:8080
export LLM_PROXY_URL=http://localhost:8080
export GITHUB_TOKEN_ID=1

# Optional
export TEST_MODE=false
```

## Best Practices

1. **Security**:
   - Never commit secrets to repositories
   - Use environment variables or secret management
   - Validate webhook signatures

2. **Error Handling**:
   - Implement retry logic for transient failures
   - Log all operations for debugging
   - Send notifications on failures

3. **Rate Limiting**:
   - Respect API rate limits
   - Implement queuing for high-volume scenarios
   - Use exponential backoff

4. **Testing**:
   - Test automation scripts in test mode first
   - Use test repositories for validation
   - Monitor provenance events

## Troubleshooting

### Common Issues

1. **Authentication Failures**:
   - Verify Keycloak credentials
   - Check JWT token expiration
   - Ensure service URLs are correct

2. **GitHub MCP Server Issues**:
   - Verify GitHub token permissions
   - Check Kubernetes cluster access
   - Review MCP server logs

3. **LLM Generation Failures**:
   - Check LLM proxy connectivity
   - Verify model availability
   - Review prompt formatting

### Debug Mode

Run with debug logging:

```bash
export PYTHONVERBOSE=1
python3 main.py coding --task-data '{...}'
```

## Support

For issues or questions:
- Check agent logs in provenance events
- Review integration proxy logs
- Contact Sentrius support: marc@sentrius.io
