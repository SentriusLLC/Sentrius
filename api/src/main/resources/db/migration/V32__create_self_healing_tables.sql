-- Self-healing configuration table
-- Stores patching policies for pods/services
CREATE TABLE self_healing_config (
    id BIGSERIAL PRIMARY KEY,
    pod_name VARCHAR(255) NOT NULL,
    pod_type VARCHAR(255),
    patching_policy VARCHAR(50) NOT NULL DEFAULT 'NEVER', -- IMMEDIATE, OFF_HOURS, NEVER
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_pod_config UNIQUE (pod_name)
);

-- Self-healing session tracking table
-- Tracks each healing attempt for errors
CREATE TABLE self_healing_session (
    id BIGSERIAL PRIMARY KEY,
    error_output_id BIGINT REFERENCES error_output(id),
    agent_id VARCHAR(255),
    pod_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, ANALYZING, FIXING, COMPLETED, FAILED
    is_security_concern BOOLEAN,
    security_analysis TEXT,
    healing_actions TEXT,
    github_pr_url VARCHAR(500),
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT
);

-- Add self-healing columns to error_output table
ALTER TABLE error_output ADD COLUMN IF NOT EXISTS is_security_concern BOOLEAN;
ALTER TABLE error_output ADD COLUMN IF NOT EXISTS healing_status VARCHAR(50); -- NONE, QUEUED, IN_PROGRESS, COMPLETED, FAILED
ALTER TABLE error_output ADD COLUMN IF NOT EXISTS healing_session_id BIGINT REFERENCES self_healing_session(id);

-- Create indexes for performance
CREATE INDEX idx_self_healing_config_policy ON self_healing_config(patching_policy);
CREATE INDEX idx_self_healing_config_enabled ON self_healing_config(enabled);
CREATE INDEX idx_self_healing_session_status ON self_healing_session(status);
CREATE INDEX idx_self_healing_session_error ON self_healing_session(error_output_id);
CREATE INDEX idx_error_output_healing_status ON error_output(healing_status);
