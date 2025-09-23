# Session Summarization

This feature enables automatic capture and analysis of both RDP and SSH session activity using AI-powered summarization.

## Overview

When enabled, the system will:
1. **For RDP sessions**: Intercept PNG/IMG instructions from Guacamole protocol and store screenshots in database
2. **For SSH sessions**: Capture terminal command logs and output
3. Analyze session data using the analytics agent (runs every 2 minutes)
4. Generate comprehensive summaries with key activities and risk indicators

## Architecture

Both RDP and SSH session summarization share a common architecture:

```
Session Activity
    ↓
Capture Layer (RDP: Screenshots | SSH: Terminal Logs)
    ↓
Database Storage (PostgreSQL)
    ↓
Analytics Agent (Scheduled Task - every 2 minutes)
    ↓
AI Analysis & Summary Generation
    ↓
Summary Storage
```

## Configuration

### Global Settings

Enable both RDP and SSH session analytics:

```properties
# Enable RDP screenshot capture
rdp.screenshot.enabled=true
rdp.screenshot.capture-interval-instructions=50

# Enable session analytics agents
agents.rdp-session-analytics.enabled=true
agents.ssh-session-analytics.enabled=true
```

### RDP Session Summarization

**How it works:**
- Intercepts PNG/IMG instructions from Guacamole protocol stream
- Extracts base64-encoded image data from RDP display updates
- Stores actual screenshot data in PostgreSQL BLOB
- Samples every N instructions to avoid overwhelming the system
- Processes screenshots asynchronously via analytics agent

**Configuration:**
```properties
rdp.screenshot.enabled=true
rdp.screenshot.capture-interval-instructions=50  # Capture every N instructions
```

### SSH Session Summarization

**How it works:**
- Captures terminal output and commands in `terminal_log` table
- Analytics agent processes closed sessions
- Extracts commands and key activities from terminal logs
- Generates structured summaries with command history
- Identifies potential security concerns

**Configuration:**
```properties
agents.ssh-session-analytics.enabled=true
```

## Database Schema

### RDP Session Tables

**`rdp_session_screenshots`**
- Stores PNG image data as BYTEA
- Tracks processing status
- Indexed for efficient querying

**`rdp_session_summaries`**
- AI-generated session summaries
- Key activities and risk indicators
- Screenshot count and timeline

### SSH Session Tables

**`ssh_session_summaries`**
- AI-generated terminal session summaries
- Commands executed during session
- Key activities and risk indicators  
- Terminal log count and timeline
- Foreign key to `session_log` table

## Analytics Agents

### RdpSessionSummarizationAgent

- **Schedule**: Every 2 minutes
- **Process**: Finds unprocessed RDP screenshots
- **Analysis**: Visual analysis of screenshot data
- **Output**: Structured summary with timeline and activities

### SshSessionSummarizationAgent

- **Schedule**: Every 2 minutes
- **Process**: Finds closed sessions without summaries
- **Analysis**: Text analysis of terminal logs and commands
- **Output**: Structured summary with command history

## Common Features

Both agents share:
- **Conditional Enablement**: Only run when explicitly enabled
- **LLM Integration Ready**: Architecture prepared for OpenAI Vision/Chat API
- **Asynchronous Processing**: No impact on active sessions
- **Error Handling**: Robust error handling and logging
- **Database Transactions**: Atomic operations for data integrity

## Performance

### RDP Session Summarization
- Screenshot size: 5-50 KB per PNG (from Guacamole protocol)
- Typical 1-hour session: 50-200 screenshots = 2-10 MB storage
- Processing time: ~2-5 seconds per session
- No filesystem I/O - all data in PostgreSQL

### SSH Session Summarization
- Terminal logs: Variable size depending on command output
- Typical session: 10-1000 terminal log entries
- Processing time: ~1-3 seconds per session
- Leverages existing terminal log infrastructure

## Security Considerations

- **Sensitive Data**: Both screenshots and terminal logs contain sensitive information
- **Access Controls**: Implement proper authentication and authorization
- **Data Retention**: Establish policies to delete old summaries
- **Compliance**: Ensure summaries meet regulatory requirements
- **Encryption**: All data stored in PostgreSQL with standard database security

## API Endpoints (Future)

Planned API endpoints for accessing summaries:

```
# RDP Sessions
GET /api/v1/rdp/sessions/{sessionId}/summary
GET /api/v1/rdp/sessions/{sessionId}/screenshots
GET /api/v1/rdp/summaries?user={username}&start={date}&end={date}

# SSH Sessions  
GET /api/v1/ssh/sessions/{sessionId}/summary
GET /api/v1/ssh/sessions/{sessionId}/logs
GET /api/v1/ssh/summaries?user={username}&start={date}&end={date}

# Unified
GET /api/v1/sessions/summaries?type={rdp|ssh}&user={username}
```

## Troubleshooting

### RDP Screenshots Not Being Captured
- Check `rdp.screenshot.enabled=true` in configuration
- Verify Guacamole WebSocket connections are working
- Check logs for `RdpScreenshotCaptureService` messages
- Ensure database tables were created (V27 migration)

### SSH Summaries Not Being Generated
- Ensure `agents.ssh-session-analytics.enabled=true`
- Check that sessions have closed (`session_log.closed = true`)
- Verify terminal logs exist in `terminal_log` table
- Check analytics agent logs for processing errors
- Ensure database table was created (V28 migration)

### General Issues
- Verify both analytics agents are running (check logs every 2 minutes)
- Confirm LLM integration is configured (optional but recommended)
- Check database connectivity and table existence
- Review agent logs for specific error messages

## Future Enhancements

Both systems are ready for:

### LLM Integration
- **RDP**: OpenAI Vision API for detailed screenshot analysis
- **SSH**: OpenAI Chat API for command interpretation and risk assessment
- Unified prompt engineering for consistent analysis
- Custom models for specialized security analysis

### Real-Time Analysis
- Stream processing for immediate threat detection
- Live alerts for suspicious activities
- Integration with SIEM systems
- Automated response workflows

### Advanced Analytics
- Pattern detection across sessions
- User behavior profiling
- Anomaly detection using ML models
- Predictive analytics for security incidents
- Integration with Neo4j for relationship analysis

### Reporting
- Executive dashboards
- Compliance reports
- Trend analysis
- Custom report generation

## Migration Notes

### Database Migrations

- **V27**: RDP session screenshot tables
- **V28**: SSH session summary table

Both migrations are idempotent and will automatically run on application startup via Flyway.

### Backward Compatibility

- All features are disabled by default
- No impact on existing RDP or SSH functionality when disabled
- Existing terminal logs and sessions remain unchanged
- Database migrations are additive only

## Example Use Cases

### Security Auditing
- Review what commands users executed during SSH sessions
- Analyze RDP sessions for unauthorized activities
- Identify privilege escalation attempts
- Track file transfers and modifications

### Compliance
- Generate audit trails for SOC2, HIPAA, PCI-DSS
- Document user activities for regulatory requirements
- Demonstrate access controls and monitoring

### Incident Response
- Reconstruct user actions during security incidents
- Identify scope of potential breaches
- Provide evidence for forensic analysis

### Productivity Monitoring
- Understand how users interact with systems
- Identify training needs
- Optimize workflows based on usage patterns
