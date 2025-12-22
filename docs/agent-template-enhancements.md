# Agent Template Enhancements

## Overview

Agent templates have been enhanced to provide comprehensive agent definitions including identity, purpose, goals, guardrails, and trust policy references. This enables better-defined agents with clear mission statements and security boundaries.

## New Template Fields

### 1. Identity Configuration
**Field:** `identity` (JSONB)

Defines the agent's identity configuration for authentication and authorization.

**Structure:**
```json
{
  "issuer": "sentrius-keycloak",
  "subjectPrefix": "service-account-",
  "mfaRequired": false,
  "certificateAuthority": "sentrius-ca"
}
```

**Purpose:** 
- Specifies the identity provider (issuer)
- Defines subject naming conventions
- Sets authentication requirements (MFA)
- References certificate authorities for PKI-based authentication

### 2. Purpose
**Field:** `purpose` (TEXT)

A clear, concise description of the agent's primary mission and reason for existence.

**Example:**
```
Provide helpful, accurate, and conversational assistance to users for general queries, 
task guidance, and information retrieval.
```

**Guidelines:**
- Should be 1-2 sentences
- Focus on the "what" and "why"
- Be specific but not overly technical

### 3. Goals
**Field:** `goals` (TEXT)

Specific, measurable objectives the agent should achieve.

**Example:**
```
1. Respond to user queries with accurate and relevant information
2. Maintain conversation context and coherence
3. Provide clear and actionable guidance when requested
4. Learn from feedback to improve response quality
```

**Guidelines:**
- Use numbered lists for clarity
- Make goals SMART (Specific, Measurable, Achievable, Relevant, Time-bound where applicable)
- Limit to 3-5 key goals
- Focus on outcomes, not implementation details

### 4. Guardrails
**Field:** `guardrails` (JSONB)

Defines constraints, limits, and safety boundaries for the agent.

**Structure:**
```json
{
  "maxTokensPerRequest": 2000,
  "restrictions": [
    "no-code-execution",
    "no-system-access",
    "read-only-database"
  ],
  "rateLimitPerMinute": 5.0,
  "requireApprovalFor": [
    "destructive-operations",
    "external-api-calls"
  ],
  "allowedApis": [
    "internal-knowledge-base",
    "public-documentation"
  ]
}
```

**Purpose:**
- Prevent unauthorized or dangerous actions
- Rate limit to prevent abuse
- Define approval workflows for sensitive operations
- Whitelist approved resources

### 5. Trust Policy ID
**Field:** `trustPolicyId` (VARCHAR)

Reference to an ATPL (Agent Trust Policy Language) policy that governs agent behavior and permissions.

**Example:** `default-chat-policy`, `security-agent-policy`, `developer-agent-policy`

**Purpose:**
- Links agent to existing trust policies
- Enables centralized policy management
- Allows policy-based access control
- Supports zero-trust architecture

### 6. Launch Configuration
**Field:** `launchConfiguration` (JSONB)

Launch-specific settings including resource limits and environment variables.

**Structure:**
```json
{
  "resources": {
    "cpuLimit": "1000m",
    "memoryLimit": "1Gi",
    "diskLimit": "10Gi"
  },
  "environmentVariables": {
    "LOG_LEVEL": "INFO",
    "MAX_RETRIES": "3",
    "TIMEOUT_SECONDS": "30"
  },
  "restartPolicy": "OnFailure",
  "priorityClass": "high-priority"
}
```

**Purpose:**
- Define resource constraints for containerized agents
- Set environment-specific configuration
- Configure restart and failure handling
- Prioritize critical agents

## Default System Templates

The system includes five pre-configured templates demonstrating best practices:

### 1. Chat Assistant
- **Purpose:** Conversational Q&A and task assistance
- **Trust Policy:** `default-chat-policy`
- **Guardrails:** Limited tokens, no code execution, rate-limited
- **Use Case:** General-purpose user interaction

### 2. Code Review Agent
- **Purpose:** Automated code quality and security analysis
- **Trust Policy:** `developer-agent-policy`
- **Guardrails:** Read-only code access, no destructive operations
- **Use Case:** Pull request reviews, static analysis

### 3. Security Audit Agent
- **Purpose:** Vulnerability scanning and compliance verification
- **Trust Policy:** `security-agent-policy`
- **Guardrails:** Read-only access, audit logging, no modifications
- **Use Case:** Security assessments, compliance audits

### 4. Monitoring Agent
- **Purpose:** Real-time system health and performance monitoring
- **Trust Policy:** `monitoring-agent-policy`
- **Guardrails:** Metrics read-only, limited alerting
- **Use Case:** System observability, incident detection

### 5. Data Analysis Agent
- **Purpose:** Statistical insights and data processing
- **Trust Policy:** `analytics-agent-policy`
- **Guardrails:** Read-only database, no PII exposure, rate-limited
- **Use Case:** Business intelligence, trend analysis

## API Endpoints

### Get All Templates
```http
GET /api/v1/agent/templates
```
Returns all enabled agent templates.

### Get Template by ID
```http
GET /api/v1/agent/templates/{id}
```
Returns a specific template with all configuration details.

### Create Template
```http
POST /api/v1/agent/templates
Content-Type: application/json

{
  "name": "Custom Agent",
  "description": "Description",
  "agentType": "custom",
  "purpose": "Primary mission statement",
  "goals": "1. Goal one\n2. Goal two",
  "identity": "{...}",
  "guardrails": "{...}",
  "trustPolicyId": "policy-id",
  "launchConfiguration": "{...}"
}
```

### Update Template
```http
PUT /api/v1/agent/templates/{id}
Content-Type: application/json
```
Updates an existing template (system templates cannot be modified).

### Delete Template
```http
DELETE /api/v1/agent/templates/{id}
```
Deletes a template (system templates cannot be deleted).

### Prepare Launch
```http
POST /api/v1/agent/templates/{id}/prepare-launch?agentName=my-agent
```
Prepares an AgentRegistrationDTO with full template configuration for the launcher service.

### Launch Agent
```http
POST /api/v1/agent/templates/{id}/launch?agentName=my-agent
```
Initiates agent launch from template with proper identity and policy configuration.

## Database Schema

```sql
-- Enhanced agent_templates table
CREATE TABLE agent_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    agent_type VARCHAR(255) NOT NULL,
    icon VARCHAR(100),
    category VARCHAR(100),
    default_configuration TEXT,
    
    -- New enhanced fields
    identity JSONB,
    purpose TEXT,
    goals TEXT,
    guardrails JSONB,
    trust_policy_id VARCHAR(255),
    launch_configuration JSONB,
    
    system_template BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER DEFAULT 0,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX idx_agent_templates_trust_policy 
    ON agent_templates(trust_policy_id) WHERE trust_policy_id IS NOT NULL;
```

## Integration with Agent Launcher

When launching an agent from a template, the system:

1. Retrieves template configuration
2. Validates trust policy reference
3. Applies identity configuration to Keycloak
4. Sets guardrails in agent runtime
5. Configures resource limits
6. Launches agent pod/container
7. Records launch in agent_launches table

## Best Practices

### Identity Configuration
- Use consistent subject prefixes for easy identification
- Enable MFA for high-privilege agents
- Reference appropriate certificate authorities

### Purpose and Goals
- Keep purpose statements concise and clear
- Make goals measurable and specific
- Review and update goals based on agent performance

### Guardrails
- Start conservative, relax as needed
- Document why each restriction exists
- Test guardrails thoroughly
- Monitor for violations

### Trust Policies
- Use existing policies when possible
- Create new policies only when requirements differ significantly
- Version policy IDs for tracking changes
- Document policy purpose and scope

### Launch Configuration
- Set appropriate resource limits based on workload
- Use environment variables for configuration
- Configure restart policies based on agent criticality
- Monitor resource usage and adjust as needed

## Migration from Legacy Templates

Existing templates without enhanced fields will continue to work with default values:
- `identity`: null (uses system defaults)
- `purpose`: null (inferred from description)
- `goals`: null (no explicit goals)
- `guardrails`: null (no additional constraints)
- `trustPolicyId`: null (uses default policy)
- `launchConfiguration`: null (uses system defaults)

To enhance legacy templates, use the UI or API to populate these fields.

## Security Considerations

1. **Identity Isolation:** Each agent should have unique identity credentials
2. **Least Privilege:** Guardrails should enforce minimum necessary permissions
3. **Trust Verification:** Trust policies should be validated before launch
4. **Audit Logging:** All agent actions should be logged and monitored
5. **Resource Limits:** Prevent resource exhaustion attacks
6. **Input Validation:** Validate all JSON configuration fields
7. **Policy Enforcement:** Trust policies must be actively enforced at runtime

## Future Enhancements

- Visual policy editor for guardrails
- Template versioning and rollback
- Template inheritance and composition
- A/B testing for template configurations
- Automated goal achievement tracking
- Dynamic guardrail adjustment based on trust score
- Template marketplace for sharing common patterns
