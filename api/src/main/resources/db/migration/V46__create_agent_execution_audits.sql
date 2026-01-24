-- Create agent_execution_audits table for tracking agent execution summaries
CREATE TABLE IF NOT EXISTS agent_execution_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id VARCHAR(255) NOT NULL,
    execution_id VARCHAR(255) NOT NULL,
    agent_type VARCHAR(100) NOT NULL,
    executed_by VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    summary TEXT,
    resource_links TEXT,
    pod_logs TEXT,
    exit_code INTEGER,
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    CONSTRAINT unique_execution_id UNIQUE (execution_id)
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_agent_execution_audits_agent_id ON agent_execution_audits(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_execution_audits_execution_id ON agent_execution_audits(execution_id);
CREATE INDEX IF NOT EXISTS idx_agent_execution_audits_agent_type ON agent_execution_audits(agent_type);
CREATE INDEX IF NOT EXISTS idx_agent_execution_audits_status ON agent_execution_audits(status);
CREATE INDEX IF NOT EXISTS idx_agent_execution_audits_executed_by ON agent_execution_audits(executed_by);
CREATE INDEX IF NOT EXISTS idx_agent_execution_audits_start_time ON agent_execution_audits(start_time DESC);
