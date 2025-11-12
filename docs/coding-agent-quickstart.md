# Coding Agent - Quick Reference

## TL;DR

The Coding Agent automates code generation and PR submission from JIRA/GitHub issues.

## Quick Start

```bash
# Install dependencies
cd python-agent
pip install -r requirements.txt

# Configure
export KEYCLOAK_BASE_URL=http://localhost:8180
export INTEGRATION_PROXY_URL=http://localhost:8080
export GITHUB_TOKEN_ID=1

# Run
python main.py coding --task-data '{
  "operation": "handle_jira_issue",
  "issue_key": "PROJECT-123",
  "repo": "owner/repository"
}'
```

## Commands

### Handle JIRA Issue
```bash
python main.py coding --task-data '{
  "operation": "handle_jira_issue",
  "issue_key": "PROJECT-123",
  "repo": "owner/repo"
}'
```

### Handle GitHub Issue
```bash
python main.py coding --task-data '{
  "operation": "handle_github_issue",
  "repo": "owner/repo",
  "issue_number": 456
}'
```

### Create PR Directly
```bash
python main.py coding --task-data '{
  "operation": "create_pr",
  "repo": "owner/repo",
  "title": "Fix bug",
  "description": "Description"
}'
```

## Configuration

### Required Environment Variables
```bash
KEYCLOAK_BASE_URL          # Keycloak server URL
KEYCLOAK_CLIENT_ID         # Client ID (default: python-agents)
KEYCLOAK_CLIENT_SECRET     # Client secret
INTEGRATION_PROXY_URL      # Integration proxy URL
LLM_PROXY_URL             # LLM proxy URL
GITHUB_TOKEN_ID           # GitHub token ID
```

### Optional Environment Variables
```bash
LLM_MODEL                 # LLM model (default: gpt-4)
TEST_MODE                 # Test mode (default: false)
```

## Common Workflows

### 1. JIRA → Code → PR → JIRA Comment
```
JIRA Issue → Generate Code → Create PR → Update JIRA
```

### 2. GitHub Issue → Code → PR
```
GitHub Issue → Generate Code → Create PR
```

### 3. Direct PR Creation
```
Code Changes → Create PR
```

## Automation Examples

### JIRA Webhook
```bash
./examples/jira-webhook-handler.sh PROJECT-123 owner/repo
```

### GitHub Webhook
```bash
./examples/github-webhook-handler.sh owner/repo 456
```

### Python Script
```python
from examples.automation_example import CodingAgentAutomation

automation = CodingAgentAutomation()
automation.handle_jira_issue("PROJECT-123", "owner/repo")
```

## Testing

### Test Mode (No External Services)
```bash
TEST_MODE=true python main.py coding --task-data '{...}'
```

### Run Unit Tests
```bash
python -m unittest tests.test_coding_agent -v
```

## Troubleshooting

### Issue: Authentication Failed
**Solution**: Verify Keycloak credentials and URL

### Issue: GitHub MCP Server Not Available
**Solution**: Check Kubernetes cluster access and GitHub token

### Issue: LLM Generation Failed
**Solution**: Verify LLM proxy is running and model is available

### Issue: PR Creation Failed
**Solution**: Check GitHub token permissions and repository access

## Architecture

```
Coding Agent
    ├── GitHub MCP Server (existing)
    ├── JIRA Proxy (existing)
    ├── LLM Proxy (existing)
    ├── Keycloak (existing)
    └── Provenance System (existing)
```

## Files

- `agents/coding/coding_agent.py` - Main agent
- `coding.yaml` - Configuration
- `agents/coding/README.md` - Full documentation
- `examples/` - Integration examples
- `tests/test_coding_agent.py` - Unit tests

## Documentation

- Full Guide: `agents/coding/README.md`
- Examples: `examples/README.md`
- Technical Details: `docs/coding-agent-implementation.md`
- This Guide: Quick reference

## Support

For issues: marc@sentrius.io
