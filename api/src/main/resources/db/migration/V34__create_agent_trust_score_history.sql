-- Create agent_trust_score_history table for tracking trust scores over time
CREATE TABLE IF NOT EXISTS agent_trust_score_history (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(255) NOT NULL,
    agent_name VARCHAR(255),
    trust_score INTEGER NOT NULL,
    identity_score DOUBLE PRECISION,
    provenance_score DOUBLE PRECISION,
    runtime_score DOUBLE PRECISION,
    behavior_score DOUBLE PRECISION,
    evaluation_result VARCHAR(50),
    policy_id VARCHAR(255),
    timestamp TIMESTAMP NOT NULL,
    prior_runs INTEGER,
    incident_count INTEGER,
    enclave_verified BOOLEAN,
    evaluation_notes TEXT
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_agent_trust_score_history_agent_id_timestamp 
    ON agent_trust_score_history(agent_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_agent_trust_score_history_timestamp 
    ON agent_trust_score_history(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_agent_trust_score_history_agent_id 
    ON agent_trust_score_history(agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_trust_score_history_evaluation_result 
    ON agent_trust_score_history(evaluation_result);
