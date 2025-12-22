-- Enhance agent_templates table with identity, purpose, goals, guardrails, and trust policy
ALTER TABLE agent_templates 
ADD COLUMN IF NOT EXISTS identity JSONB,
ADD COLUMN IF NOT EXISTS purpose TEXT,
ADD COLUMN IF NOT EXISTS goals TEXT,
ADD COLUMN IF NOT EXISTS guardrails JSONB,
ADD COLUMN IF NOT EXISTS trust_policy_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS launch_configuration JSONB;

-- Add comment documentation for new columns
COMMENT ON COLUMN agent_templates.identity IS 'JSON object defining agent identity (issuer, subject_prefix, certificate_authority, etc.)';
COMMENT ON COLUMN agent_templates.purpose IS 'Clear description of the agent primary purpose and mission';
COMMENT ON COLUMN agent_templates.goals IS 'Specific, measurable goals the agent should achieve';
COMMENT ON COLUMN agent_templates.guardrails IS 'JSON object defining constraints, limits, and safety boundaries for the agent';
COMMENT ON COLUMN agent_templates.trust_policy_id IS 'Reference to ATPL trust policy that should be applied to agents launched from this template';
COMMENT ON COLUMN agent_templates.launch_configuration IS 'JSON object with launch-specific configuration (resources, environment variables, etc.)';

-- Create index for trust_policy_id lookups
CREATE INDEX IF NOT EXISTS idx_agent_templates_trust_policy 
    ON agent_templates(trust_policy_id) WHERE trust_policy_id IS NOT NULL;
