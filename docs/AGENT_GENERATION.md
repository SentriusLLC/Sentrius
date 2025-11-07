# Agent Generation Feature

## Overview

The Agent Generation feature allows users to create new instances of agents (called "generations") based on existing agent configurations. This feature is accessible from the Users Management page under the NPEs (Non-Person Entities) section.

## Use Cases

- **Testing agent variations**: Create a new agent with modified policies to test different configurations
- **Scaling agents**: Quickly spin up multiple instances of a working agent
- **Policy evolution**: Create new generations with updated policies while keeping the original agent running
- **Development**: Create test instances of production agents for development purposes

## How to Use

### Step 1: Navigate to Users Management

1. Log in to Sentrius with an account that has `CAN_MANAGE_APPLICATION` permissions
2. Navigate to **Users** > **List Users** from the sidebar
3. Scroll down to the **NPEs** (Non-Person Entities) table

### Step 2: Create a Generation

1. Locate the agent you want to use as a template
2. Click the **Create Generation** button (green button) in the Actions column
3. A modal dialog will open with the following fields:

   - **New Agent Name**: The name for your new agent generation (auto-populated with parent name + timestamp)
   - **Agent Type**: Select the type of agent (chat, atpl-helper, or mcp)
   - **Agent Policy (ATPL)**: The YAML policy for the agent (pre-populated with parent agent's policy)

### Step 3: Customize the Configuration

1. **Modify the Agent Name** (optional): Change the suggested name to something more meaningful
2. **Select Agent Type** (optional): Choose a different agent type if needed
3. **Edit the Policy** (optional): 
   - The policy editor shows the parent agent's current policy
   - A new unique `policy_id` is automatically generated to avoid conflicts
   - You can customize any aspect of the policy:
     - Add or remove capabilities
     - Modify endpoint permissions
     - Adjust behavior settings
     - Update ZTAT requirements

### Step 4: Launch the Agent

1. Review your configuration
2. Click the **Launch Agent** button (with rocket icon)
3. Wait for confirmation (the system will show a success message)
4. The new agent will appear in the NPEs table within a few moments

## Technical Details

### Policy ID Generation

When creating a generation, a new unique `policy_id` is automatically generated using:
- `crypto.randomUUID()` on modern browsers
- A fallback UUID v4 implementation for older browsers

This ensures that each generation has its own policy instance and won't conflict with the parent or other generations.

### Agent Lifecycle

1. **Launch Request**: User clicks "Launch Agent" → Frontend sends request to `/api/v1/agent/bootstrap/launcher/create`
2. **Policy Caching**: The backend caches the policy ID for the new agent
3. **Pod Creation**: The agent-launcher service creates a Kubernetes pod for the new agent
4. **Registration**: The agent registers with Sentrius and receives its configuration
5. **Policy Assignment**: The cached policy is assigned to the agent during registration

### Permissions Required

- `CAN_MANAGE_APPLICATION`: Required to access the Users page and create agent generations

### Browser Compatibility

The feature is compatible with:
- Modern browsers that support `crypto.randomUUID()` (Chrome 92+, Firefox 95+, Safari 15.4+)
- Older browsers via UUID polyfill fallback

## Best Practices

1. **Naming Convention**: Use descriptive names that indicate the purpose or configuration variant
   - Good: `terminal-helper-readonly-gen-001`
   - Avoid: `agent-gen-12345678`

2. **Policy Customization**: 
   - Always review the auto-generated `policy_id` to ensure it's unique
   - Test policy changes on generation agents before applying to production agents
   - Document significant policy modifications in the `description` field

3. **Resource Management**:
   - Delete unused generations to free up cluster resources
   - Monitor agent health via the Ping feature
   - Use the Audit Graph to understand agent interactions

4. **Testing Workflow**:
   - Create a generation with test policy
   - Verify behavior via the agent designer or chat interface
   - If successful, update the parent agent's policy or create permanent generations

## Troubleshooting

### Agent doesn't appear in list
- Wait 30-60 seconds for the pod to start
- Check Kubernetes pod status via kubectl or agent launcher logs
- Verify the agent has access to required services (Keycloak, database)

### Policy validation errors
- Ensure the policy YAML is well-formed
- Verify all required fields are present (policy_id, version, description)
- Check that endpoint paths are valid

### Launch fails
- Check browser console for error messages
- Verify your user has `CAN_MANAGE_APPLICATION` permissions
- Ensure the Kubernetes cluster has available resources
- Review agent-launcher service logs for detailed error information

## API Reference

### POST /api/v1/agent/bootstrap/launcher/create

Creates a new agent pod with the specified configuration.

**Request Body:**
```json
{
  "agentName": "my-agent-gen-001",
  "agentType": "chat",
  "agentPolicyId": "f3326ce2-f46f-405d-94b6-bda2b26db423",
  "clientId": "",
  "agentCallbackUrl": ""
}
```

**Response:**
```json
{
  "status": "success"
}
```

## Related Features

- **Agent Designer**: Create agents with an interactive chat interface
- **Policy Editor**: Edit existing agent policies
- **Agent Audit**: View agent communication history and connections
- **Agent Ping**: Check agent health and version information

## Future Enhancements

Potential improvements for this feature:
- Bulk generation: Create multiple generations at once
- Template library: Save and reuse policy templates
- Diff view: Compare policy changes between parent and generation
- Auto-scaling: Automatically create generations based on load
- Generation lineage: Track parent-child relationships between agents
