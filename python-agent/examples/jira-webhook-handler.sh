#!/bin/bash
# Example script demonstrating coding agent integration with JIRA webhook

set -e

# Configuration
AGENT_URL="${AGENT_URL:-http://localhost:8093}"
JIRA_WEBHOOK_SECRET="${JIRA_WEBHOOK_SECRET:-your-webhook-secret}"

# Parse JIRA webhook payload
ISSUE_KEY="$1"
REPO="$2"

if [ -z "$ISSUE_KEY" ] || [ -z "$REPO" ]; then
    echo "Usage: $0 <ISSUE_KEY> <REPO>"
    echo "Example: $0 PROJECT-123 owner/repository"
    exit 1
fi

echo "Processing JIRA issue: $ISSUE_KEY for repository: $REPO"

# Prepare task data
TASK_DATA=$(cat <<EOF
{
  "operation": "handle_jira_issue",
  "issue_key": "$ISSUE_KEY",
  "repo": "$REPO",
  "context": {
    "language": "Python",
    "framework": "Flask",
    "automated": true
  }
}
EOF
)

# Call coding agent
echo "Invoking coding agent..."
python3 /path/to/sentrius/python-agent/main.py coding \
    --task-data "$TASK_DATA"

echo "Coding agent completed for issue: $ISSUE_KEY"
