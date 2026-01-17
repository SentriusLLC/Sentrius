-- Support executing scripts directly from suggestions without requiring conversion to automation
-- Make automation_id nullable and add suggestion_id to track executions from suggestions

ALTER TABLE automation_executions
    ALTER COLUMN automation_id DROP NOT NULL,
    ADD COLUMN suggestion_id BIGINT REFERENCES automation_suggestions(id) ON DELETE CASCADE;

-- Create index on suggestion_id for performance
CREATE INDEX idx_automation_executions_suggestion ON automation_executions(suggestion_id);

-- Add constraint to ensure either automation_id or suggestion_id is set (but not both)
ALTER TABLE automation_executions
    ADD CONSTRAINT chk_automation_or_suggestion CHECK (
        (automation_id IS NOT NULL AND suggestion_id IS NULL) OR
        (automation_id IS NULL AND suggestion_id IS NOT NULL)
    );
