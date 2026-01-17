-- Add columns to track execution metadata
ALTER TABLE automation_executions
    ADD COLUMN executed_by_user_id BIGINT REFERENCES users(id),
    ADD COLUMN status VARCHAR(50) DEFAULT 'SUCCESS',
    ADD COLUMN exit_code INTEGER;

-- Create index on executed_by_user_id for performance
CREATE INDEX idx_automation_executions_user ON automation_executions(executed_by_user_id);

-- Create index on status for filtering
CREATE INDEX idx_automation_executions_status ON automation_executions(status);
