#!/bin/bash
# Example script demonstrating coding agent integration with GitHub webhooks

set -e

# Configuration
AGENT_URL="${AGENT_URL:-http://localhost:8093}"
GITHUB_WEBHOOK_SECRET="${GITHUB_WEBHOOK_SECRET:-your-webhook-secret}"

# Parse GitHub webhook payload
REPO="$1"
ISSUE_NUMBER="$2"

if [ -z "$REPO" ] || [ -z "$ISSUE_NUMBER" ]; then
    echo "Usage: $0 <REPO> <ISSUE_NUMBER>"
    echo "Example: $0 owner/repository 456"
    exit 1
fi

echo "Processing GitHub issue: $REPO#$ISSUE_NUMBER"

# Prepare task data
TASK_DATA=$(cat <<EOF
{
  "operation": "handle_github_issue",
  "repo": "$REPO",
  "issue_number": $ISSUE_NUMBER,
  "context": {
    "language": "Java",
    "framework": "Spring Boot",
    "automated": true
  }
}
EOF
)

# Call coding agent
echo "Invoking coding agent..."
python3 /path/to/sentrius/python-agent/main.py coding \
    --task-data "$TASK_DATA"

echo "Coding agent completed for issue: $REPO#$ISSUE_NUMBER"
