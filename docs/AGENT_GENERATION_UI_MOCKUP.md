# UI Mockup - Agent Generation Feature

## NPEs Table View

The NPEs (Non-Person Entities) table in the List Users page now includes a "Create Generation" button:

```
+--------------------------------------------------------------------------------+
| NPEs                                                                           |
+--------------------------------------------------------------------------------+
| Name          | Username              | Last Seen        | Actions           |
+--------------------------------------------------------------------------------+
| Terminal Help | terminal-helper       | 2 minutes ago    | [Create Generation] |
|               |                       |                  | [Ping] [View Policy]|
|               |                       |                  | [Edit Policy]       |
|               |                       |                  | [Audit Graph]       |
|               |                       |                  | [Delete]            |
+--------------------------------------------------------------------------------+
| Chat Agent    | chat-agent-001        | 5 minutes ago    | [Create Generation] |
|               |                       |                  | [Ping] [View Policy]|
|               |                       |                  | [Edit Policy]       |
|               |                       |                  | [Audit Graph]       |
|               |                       |                  | [Delete]            |
+--------------------------------------------------------------------------------+
```

**Button Styling:**
- "Create Generation" button: Green (btn-success) with white text
- Positioned as the first action button in each row
- Separated from other buttons with " | " dividers

## Modal Dialog - Create New Agent Generation

When the user clicks "Create Generation", a modal dialog appears:

```
+------------------------------------------------------------------------------+
|  Create New Generation of terminal-helper                              [X] |
+------------------------------------------------------------------------------+
| ℹ️ Creating a Generation: This will create a new agent based on the        |
|   selected agent's configuration. You can customize the agent's name,       |
|   type, and policy before launching.                                        |
+------------------------------------------------------------------------------+
|                                                                              |
| New Agent Name:                                                              |
| [terminal-helper-gen-1699876543210          ]                               |
| This will be the name of the new agent generation                           |
|                                                                              |
| Agent Type:                                                                  |
| [Chat                                       ▼]                               |
|   - Chat                                                                     |
|   - ATPL Helper                                                              |
|   - MCP                                                                      |
|                                                                              |
| Agent Policy (ATPL):                                                         |
| ┌────────────────────────────────────────────────────────────────────┐     |
| │---                                                                  │     |
| │version: "v0"                                                        │     |
| │description: "Default Policy For Unregistered Agents"               │     |
| │match:                                                               │     |
| │  agent_tags:                                                        │     |
| │    - "env:prod"                                                     │     |
| │    - "classification:observer"                                      │     |
| │behavior:                                                            │     |
| │  minimum_positive_runs: 5                                           │     |
| │  max_incidents: 1                                                   │     |
| │  incident_types:                                                    │     |
| │    denylist:                                                        │     |
| │      - "policy_violation"                                           │     |
| │actions:                                                             │     |
| │  on_success: "allow"                                                │     |
| │  on_failure: "deny"                                                 │     |
| │  capabilities:                                                      │     |
| │    primitives:                                                      │     |
| │      - id: "accessLLM"                                              │     |
| │        description: "access llm"                                    │     |
| │        endpoints:                                                   │     |
| │          - "/api/v1/chat/completions"                               │     |
| │policy_id: "a8f2c3e1-9b4d-4e6a-b1c7-3f8d9e2a6b5c"                  │     |
| └────────────────────────────────────────────────────────────────────┘     |
| Customize the policy for the new agent generation. A new unique            |
| policy_id has been generated automatically.                                |
|                                                                              |
+------------------------------------------------------------------------------+
|                                           [Cancel]  [🚀 Launch Agent]       |
+------------------------------------------------------------------------------+
```

**Modal Features:**
- **Informational Alert (Blue)**: Explains what the feature does
- **Pre-populated Fields**: 
  - Agent name uses parent name + "-gen-" + timestamp
  - Agent type defaults to "chat"
  - Policy editor shows parent's policy with new policy_id
- **Policy Editor**:
  - Dark background (#222) with green text (#00ff00)
  - Monospace font for better YAML readability
  - 400px height for comfortable editing
  - Scrollable for long policies
- **Action Buttons**:
  - Cancel (gray, btn-secondary)
  - Launch Agent (green, btn-success) with rocket emoji

## User Flow

1. **Initial State**: User sees NPEs table with "Create Generation" button
2. **Click Button**: Modal opens with pre-populated configuration
3. **Customize (Optional)**: User can modify name, type, or policy
4. **Launch**: User clicks "Launch Agent"
5. **Loading State**: Button shows "Launching..." and is disabled
6. **Success**: Alert shows "Agent generation launched successfully!"
7. **Refresh**: Table automatically refreshes after 3 seconds
8. **New Agent Visible**: New generation appears in NPEs table

## Visual Design Notes

### Color Scheme
- **Success/Create**: Green buttons (#28a745)
- **Primary Actions**: Blue buttons (#007bff)
- **Secondary Actions**: Gray buttons (#6c757d)
- **Info Alert**: Light blue background (#d1ecf1) with dark blue text (#0c5460)
- **Policy Editor**: Dark theme (black bg, green text) for terminal-like appearance

### Layout
- Modal width: Large (modal-lg, ~800px)
- Responsive design: Works on desktop and tablet
- Proper spacing: Margins and padding for readability
- Clear visual hierarchy: Headers, sections, and actions clearly separated

### Accessibility
- Close button (X) in modal header
- Cancel button for easy exit
- Clear labels for all form fields
- Help text under inputs for guidance
- High contrast text for readability

## Example: After Launching

After clicking "Launch Agent", the table updates:

```
+--------------------------------------------------------------------------------+
| NPEs                                                                           |
+--------------------------------------------------------------------------------+
| Name          | Username              | Last Seen        | Actions           |
+--------------------------------------------------------------------------------+
| Terminal Help | terminal-helper       | 2 minutes ago    | [Create Generation] |
|               |                       |                  | [Ping] [View Policy]|
|               |                       |                  | [Edit Policy]       |
|               |                       |                  | [Audit Graph]       |
|               |                       |                  | [Delete]            |
+--------------------------------------------------------------------------------+
| Terminal Gen  | terminal-helper-gen-  | Just now         | [Create Generation] |
|               | 1699876543210         |                  | [Ping] [View Policy]|
|               |                       |                  | [Edit Policy]       |
|               |                       |                  | [Audit Graph]       |
|               |                       |                  | [Delete]            |
+--------------------------------------------------------------------------------+
| Chat Agent    | chat-agent-001        | 5 minutes ago    | [Create Generation] |
|               |                       |                  | [Ping] [View Policy]|
|               |                       |                  | [Edit Policy]       |
|               |                       |                  | [Audit Graph]       |
|               |                       |                  | [Delete]            |
+--------------------------------------------------------------------------------+
```

The new generation appears in the list with:
- Name extracted from username
- Timestamp showing "Just now"
- All standard action buttons available
- Can be used as a template for further generations
