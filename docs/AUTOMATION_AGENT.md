# Automation Agent Feature

## Overview

The Automation Agent feature provides an intelligent code editor and AI assistant for creating, editing, and testing automation scripts. It includes safety analysis and the ability to test scripts on target systems via SSH.

## Key Features

### 1. Code Editor with Monaco
- Syntax highlighting for bash and python scripts
- Full-featured code editor with autocomplete
- Real-time editing with syntax validation

### 2. AI-Powered Code Generation
- Generate automation scripts from natural language descriptions
- Improve existing scripts with AI suggestions
- Chat with AI assistant for coding help

### 3. Safety Analysis
- Detects 20+ destructive commands (rm, dd, mkfs, reboot, etc.)
- Identifies dangerous file operations
- Flags security issues and risky patterns
- Color-coded risk levels: SAFE, LOW, MEDIUM, HIGH

### 4. SSH Testing
- Dry-run mode for safe validation
- Live testing on target enclaves
- Blocks destructive operations automatically
- Displays test results with stdout/stderr

## Usage

### Accessing the Feature

1. Navigate to **Automation > Suggestions** in the main menu
2. Click the **Edit** button on any automation suggestion
3. The code editor modal will open

### Editing Scripts

1. Use the Monaco editor to modify the script
2. Syntax highlighting automatically applies based on script type
3. Click **Save Script** to persist changes

### Using AI Assistant

#### Generate Code
1. Click **Generate with AI**
2. Enter a description of what you want to automate
3. AI generates a complete script
4. Review and modify as needed

#### Chat with AI
1. Type questions in the chat input box
2. AI provides real-time guidance
3. Ask for improvements, explanations, or troubleshooting

#### Improve Existing Code
1. Click **Improve Code** (via API)
2. Provide feedback on what to improve
3. AI generates an enhanced version

### Safety Analysis

1. Click **Analyze Safety** to check the script
2. Review the color-coded risk assessment:
   - **GREEN (SAFE)**: No issues detected
   - **BLUE (LOW)**: Minor warnings
   - **YELLOW (MEDIUM)**: Some concerns
   - **RED (HIGH)**: Destructive operations detected
3. Review detailed findings:
   - Destructive operations
   - Security issues
   - Quality suggestions

### Testing Scripts

#### Dry Run (Recommended First)
1. Click **Dry Run**
2. Script is validated without execution
3. Safety analysis is performed
4. No changes are made to the target system

#### Live Test
1. Click **Test on System**
2. Confirmation required for non-dry-run
3. Scripts with destructive operations are blocked
4. Results displayed with:
   - Exit code
   - Standard output
   - Standard error
   - Execution time

## API Endpoints

### Automation Suggestion Endpoints

#### Update Script
```
PUT /api/v1/automation/suggestions/{id}/script
Content-Type: application/json

{
  "script": "#!/bin/bash\necho 'Updated script'"
}
```

#### Generate Code
```
POST /api/v1/automation/suggestions/{id}/generate
Content-Type: application/json

{
  "prompt": "Create a script to install nginx with error handling"
}
```

#### Improve Code
```
POST /api/v1/automation/suggestions/{id}/improve
Content-Type: application/json

{
  "feedback": "Add logging and error handling"
}
```

#### Analyze Safety
```
POST /api/v1/automation/suggestions/{id}/analyze
```

#### Test Automation
```
POST /api/v1/automation/suggestions/{id}/test
Content-Type: application/json

{
  "script": "#!/bin/bash\necho 'test'",
  "dryRun": true
}
```

### Automation Agent Endpoints

#### Chat with Agent
```
POST /api/v1/automation/agent/chat
Content-Type: application/json

{
  "message": "How do I add error handling to a bash script?",
  "context": "Writing automation for nginx installation",
  "conversationHistory": [
    {"role": "user", "content": "Previous question"},
    {"role": "assistant", "content": "Previous answer"}
  ]
}
```

#### Analyze Code
```
POST /api/v1/automation/agent/analyze
Content-Type: application/json

{
  "code": "#!/bin/bash\nrm -rf /tmp/*",
  "scriptType": "bash"
}
```

## Safety Mechanisms

### Destructive Command Detection

The system automatically detects these categories of dangerous operations:

**File Deletion**: rm, rmdir, shred, wipe, truncate
**Disk Operations**: dd, mkfs, fdisk, parted, mkswap
**System Control**: reboot, shutdown, halt, poweroff, init
**Process Control**: kill, killall, pkill
**Service Management**: systemctl stop/disable/mask
**User Management**: userdel, groupdel, deluser, delgroup
**Network**: iptables -F, iptables -X

### Additional Safety Checks

- **System Directory Writes**: Detects writes to /etc, /boot, /sys, /proc, /dev, /bin, /sbin, /lib
- **Overly Permissive Permissions**: Flags chmod 777
- **Remote Execution**: Warns about `curl | bash` or `wget | sh`
- **Comment Filtering**: Ignores commands in comments

### Execution Blocking

Scripts flagged as HIGH risk are automatically blocked from execution unless run in dry-run mode. Users must explicitly acknowledge the risks before proceeding.

## Best Practices

1. **Always Start with Dry Run**: Test scripts in dry-run mode before live execution
2. **Use Safety Analysis**: Review the safety report before testing
3. **Leverage AI Chat**: Ask the AI for help improving safety and reliability
4. **Review Generated Code**: Always review AI-generated scripts before use
5. **Test on Non-Production Systems**: Test on development systems first
6. **Add Error Handling**: Use AI to add proper error handling to scripts
7. **Document Changes**: Add comments explaining what the script does

## Troubleshooting

### Script Blocked from Execution

**Cause**: Script contains destructive operations
**Solution**: 
- Review safety analysis
- Remove or modify destructive commands
- Use dry-run mode for validation only
- Contact administrator for exceptions if needed

### AI Generation Timeout

**Cause**: LLM service is slow or unavailable
**Solution**:
- Wait and try again
- Check LLM service status
- Use manual editing as fallback

### SSH Connection Failed

**Cause**: Target system unreachable or credentials invalid
**Solution**:
- Verify target system is online
- Check SSH credentials
- Ensure SSH port is accessible
- Review system configuration

### Monaco Editor Not Loading

**Cause**: CDN blocked or JavaScript error
**Solution**:
- Check browser console for errors
- Verify CDN is accessible
- Clear browser cache
- Try different browser

## Configuration

### LLM Service Configuration

Set these properties in `application.properties`:

```properties
sentrius.llm.endpoint=http://localhost:8080/api/v1/llm/chat
sentrius.llm.model=gpt-4
```

### Safety Configuration

Customize destructive command patterns in `AutomationTestService.java`:

```java
private static final Set<String> DESTRUCTIVE_COMMANDS = Set.of(
    "rm", "rmdir", "dd", // Add more as needed
);
```

## Examples

### Example 1: Generate Nginx Installation Script

1. Click **Edit** on a suggestion
2. Click **Generate with AI**
3. Enter: "Create a script to install nginx, enable it at startup, and configure basic security"
4. Review generated script
5. Click **Analyze Safety**
6. Click **Dry Run** to validate
7. Click **Save Script**

### Example 2: Improve Error Handling

1. Open editor with existing script
2. Type in chat: "How can I add better error handling to this script?"
3. AI provides suggestions
4. Apply changes manually or request AI to improve the entire script
5. Test with **Dry Run**
6. Save changes

### Example 3: Test on Target System

1. Edit script with safe operations (e.g., system information gathering)
2. Click **Analyze Safety** - should show SAFE
3. Click **Dry Run** - validates syntax
4. Click **Test on System** - runs on target
5. Review output for correctness
6. Save if successful

## Security Considerations

- All AI interactions are logged for audit
- SSH credentials are never exposed to the AI
- Destructive operations require explicit acknowledgment
- All tests are logged with provenance tracking
- Rate limiting prevents abuse of AI services
- User permissions control access to automation features

## Future Enhancements

Planned improvements for future releases:

- [ ] Support for PowerShell scripts
- [ ] Integration with version control
- [ ] Scheduled automation testing
- [ ] Multi-system parallel testing
- [ ] Advanced AI models for specialized tasks
- [ ] Custom safety rule configuration
- [ ] Script templates library
- [ ] Collaboration features for team editing
