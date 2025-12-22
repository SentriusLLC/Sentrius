-- Add SAG message column to agent_communications table
ALTER TABLE agent_communications ADD COLUMN IF NOT EXISTS sag_message TEXT;

-- Create an index on sag_message for faster lookups (optional but recommended)
CREATE INDEX IF NOT EXISTS idx_agent_communications_sag_message ON agent_communications(sag_message);
