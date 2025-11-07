-- V31__add_generational_lineage_to_agent_contexts.sql
-- Add generational lineage fields to agent_contexts table

-- Add generation tracking fields
ALTER TABLE agent_contexts ADD COLUMN IF NOT EXISTS generation INTEGER DEFAULT 1;
ALTER TABLE agent_contexts ADD COLUMN IF NOT EXISTS parent_id UUID;
ALTER TABLE agent_contexts ADD COLUMN IF NOT EXISTS memory_namespace VARCHAR(255);
ALTER TABLE agent_contexts ADD COLUMN IF NOT EXISTS trust_score DOUBLE PRECISION DEFAULT 0.5;
ALTER TABLE agent_contexts ADD COLUMN IF NOT EXISTS policy_id VARCHAR(255);

-- Add indexes for efficient lineage queries
CREATE INDEX IF NOT EXISTS idx_agent_contexts_parent_id ON agent_contexts(parent_id);
CREATE INDEX IF NOT EXISTS idx_agent_contexts_generation ON agent_contexts(generation);
CREATE INDEX IF NOT EXISTS idx_agent_contexts_policy_id ON agent_contexts(policy_id);

-- Add foreign key constraint for parent-child relationship
ALTER TABLE agent_contexts ADD CONSTRAINT fk_agent_contexts_parent 
    FOREIGN KEY (parent_id) REFERENCES agent_contexts(id) ON DELETE SET NULL;

-- Comment on columns
COMMENT ON COLUMN agent_contexts.generation IS 'Generation number of the agent (1 for original, 2+ for descendants)';
COMMENT ON COLUMN agent_contexts.parent_id IS 'Reference to parent agent context for lineage tracking';
COMMENT ON COLUMN agent_contexts.memory_namespace IS 'Namespace for agent memory (e.g., agents/name_v2)';
COMMENT ON COLUMN agent_contexts.trust_score IS 'Trust score for agent (0.0 to 1.0, decays with generations)';
COMMENT ON COLUMN agent_contexts.policy_id IS 'Associated ATPL policy ID for agent authorization';
