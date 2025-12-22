# Agent Templates Feature

## Overview
The Agent Templates feature allows administrators to configure pre-defined agent configurations that can be quickly launched through the UI. This streamlines the process of deploying agents with standardized settings.

## Features

### Template Management
- **Create Templates**: Define custom agent templates with specific configurations
- **Edit Templates**: Modify user-created templates (system templates are read-only)
- **Delete Templates**: Remove user-created templates
- **Category Organization**: Group templates by category (Communication, Development, Security, Operations, Analytics)
- **Display Ordering**: Control the order templates appear in the UI

### System Templates
The following templates are automatically created on system startup:

1. **Chat Assistant**
   - Type: `chat`
   - Purpose: Interactive Q&A and task assistance
   - Configuration: 2000 max tokens, 0.7 temperature, 8000 context window

2. **Code Review Agent**
   - Type: `code-review`
   - Purpose: Automated code review and quality analysis
   - Configuration: Standard review depth, security and style checks enabled

3. **Security Audit Agent**
   - Type: `security-audit`
   - Purpose: Security vulnerability scanning and compliance checking
   - Configuration: Full scan depth, OWASP and CIS compliance standards

4. **Monitoring Agent**
   - Type: `monitoring`
   - Purpose: Real-time system monitoring and alerting
   - Configuration: 60-second check interval, medium alert threshold

5. **Data Analysis Agent**
   - Type: `data-analysis`
   - Purpose: Data processing and analytical insights generation
   - Configuration: PostgreSQL data source, statistical analysis, JSON output

## Usage

### Accessing Templates
1. Navigate to **AI & Agents** > **Agent Templates** in the sidebar
2. The templates page displays all available templates in a grid layout

### Creating a Template
1. Click **Create Template** button
2. Fill in the required fields:
   - **Template Name**: Unique name for the template
   - **Description**: What the agent does
   - **Agent Type**: Type identifier (e.g., "chat", "monitoring")
   - **Category**: Select from predefined categories
   - **Icon**: FontAwesome icon class (e.g., "fa-robot")
   - **Display Order**: Numeric value for sorting
   - **Default Configuration**: JSON object with agent settings
   - **Enabled**: Toggle to enable/disable the template
3. Click **Save Template**

### Editing a Template
1. Click **Edit** button on a template card (only available for user templates)
2. Modify the fields as needed
3. Click **Save Template**

**Note**: System templates cannot be edited or deleted.

### Launching an Agent from Template
1. Go to the agent launch modal (trigger from dashboard or agents page)
2. Select **Template** option in the service type selector
3. Choose a template from the dropdown
4. Click **Launch Service**

## API Endpoints

### GET `/api/v1/agent/templates`
Get all enabled templates
- **Auth Required**: Yes (CAN_LOG_IN)
- **Returns**: Array of AgentTemplateDTO

### GET `/api/v1/agent/templates/{id}`
Get a specific template by ID
- **Auth Required**: Yes (CAN_LOG_IN)
- **Returns**: AgentTemplateDTO or 404

### GET `/api/v1/agent/templates/category/{category}`
Get templates by category
- **Auth Required**: Yes (CAN_LOG_IN)
- **Returns**: Array of AgentTemplateDTO

### POST `/api/v1/agent/templates`
Create a new template
- **Auth Required**: Yes (CAN_MANAGE_APPLICATION)
- **Body**: AgentTemplateDTO
- **Returns**: Created AgentTemplateDTO

### PUT `/api/v1/agent/templates/{id}`
Update an existing template
- **Auth Required**: Yes (CAN_MANAGE_APPLICATION)
- **Body**: AgentTemplateDTO
- **Returns**: Updated AgentTemplateDTO or error

### DELETE `/api/v1/agent/templates/{id}`
Delete a template
- **Auth Required**: Yes (CAN_MANAGE_APPLICATION)
- **Returns**: Success message or error

### POST `/api/v1/agent/templates/{id}/prepare-launch`
Prepare an agent launch configuration from a template
- **Auth Required**: Yes (CAN_MANAGE_APPLICATION)
- **Parameters**: 
  - `agentName` (required): Name for the new agent instance
  - `agentCallbackUrl` (optional): Callback URL for the agent
- **Returns**: AgentRegistrationDTO with template configuration

## Launcher Integration

To launch an agent from a template, use the following workflow:

1. **Get Template Configuration**:
   ```bash
   POST /api/v1/agent/templates/{templateId}/prepare-launch?agentName=my-agent
   ```
   Returns an `AgentRegistrationDTO` with:
   - `agentType`: The template's agent type
   - `agentTemplateId`: UUID of the template
   - `templateConfiguration`: JSON configuration from the template

2. **Launch Agent Pod**:
   ```bash
   POST /api/v1/agent/launcher/create
   Authorization: Bearer {token}
   Body: {AgentRegistrationDTO from step 1}
   ```

The agent launcher service will:
- Create a Kubernetes pod with the agent
- Pass template configuration as environment variables or config files
- Register the agent with the specified type and configuration

### Example Launch Flow

```javascript
// 1. Get template configuration
const templateId = "123e4567-e89b-12d3-a456-426614174000";
const prepareResponse = await fetch(
  `/api/v1/agent/templates/${templateId}/prepare-launch?agentName=chat-agent-1`,
  { method: 'POST', headers: { 'Authorization': 'Bearer ...' } }
);
const agentDto = await prepareResponse.json();

// 2. Launch the agent
const launchResponse = await fetch(
  '/api/v1/agent/launcher/create',
  {
    method: 'POST',
    headers: {
      'Authorization': 'Bearer ...',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(agentDto)
  }
);
```

The `AgentRegistrationDTO` includes:
- `agentTemplateId`: Links the launched agent to its template
- `templateConfiguration`: JSON configuration for the agent to use
- `agentType`: Determines which container image to use

## Template Configuration Format

Templates support a JSON configuration field for agent-specific settings. Example:

```json
{
  "maxTokens": 2000,
  "temperature": 0.7,
  "contextWindow": 8000,
  "model": "gpt-4",
  "systemPrompt": "You are a helpful assistant"
}
```

The configuration structure depends on the agent type and is validated by the agent implementation.

## Database Schema

### Table: `agent_templates`
- `id` (UUID, PK): Unique identifier
- `name` (VARCHAR, UNIQUE): Template display name
- `description` (TEXT): Template description
- `agent_type` (VARCHAR): Agent type identifier
- `icon` (VARCHAR): FontAwesome icon class
- `category` (VARCHAR): Template category
- `default_configuration` (TEXT): JSON configuration
- `system_template` (BOOLEAN): Whether it's a system template
- `enabled` (BOOLEAN): Whether the template is enabled
- `display_order` (INTEGER): Display sorting order
- `created_by` (VARCHAR): Username who created the template
- `created_at` (TIMESTAMP): Creation timestamp
- `updated_at` (TIMESTAMP): Last update timestamp

## Security Considerations

1. **Access Control**: Template management requires `CAN_MANAGE_APPLICATION` permission
2. **System Templates**: Cannot be modified or deleted by users
3. **Configuration Validation**: JSON configuration is validated before storage
4. **User Attribution**: User-created templates are tracked by creator username

## Future Enhancements

Potential improvements for future releases:
- Template versioning
- Template sharing across organizations
- Template import/export functionality
- Agent launch history tracking
- Template usage analytics
- Template approval workflows
- Custom template categories
