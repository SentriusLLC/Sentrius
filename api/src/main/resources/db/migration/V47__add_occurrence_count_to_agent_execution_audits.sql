-- Add occurrence_count and last_occurrence columns to agent_execution_audits
-- for consolidating duplicate audit entries

ALTER TABLE agent_execution_audits
ADD COLUMN IF NOT EXISTS occurrence_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE agent_execution_audits
ADD COLUMN IF NOT EXISTS last_occurrence TIMESTAMP;

-- Add comment for documentation
COMMENT ON COLUMN agent_execution_audits.occurrence_count IS 'Number of times this type of execution has occurred (for consolidated entries)';
COMMENT ON COLUMN agent_execution_audits.last_occurrence IS 'Timestamp of the most recent occurrence when entries are consolidated';

