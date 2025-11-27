-- Create policy_violation_events table for tracking policy violation events
-- These events are used to calculate behavior scores in trust evaluations
CREATE TABLE IF NOT EXISTS policy_violation_events (
    id BIGSERIAL PRIMARY KEY,
    entity_id VARCHAR(255) NOT NULL,
    entity_name VARCHAR(255),
    event_type VARCHAR(50) NOT NULL,
    approved BOOLEAN NOT NULL,
    endpoint VARCHAR(1024),
    policy_id VARCHAR(255),
    approver_id VARCHAR(255),
    ztat_request_id BIGINT,
    description TEXT,
    timestamp TIMESTAMP NOT NULL
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_pv_entity_id_timestamp 
    ON policy_violation_events(entity_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_pv_timestamp 
    ON policy_violation_events(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_pv_entity_id 
    ON policy_violation_events(entity_id);
CREATE INDEX IF NOT EXISTS idx_pv_approved 
    ON policy_violation_events(approved);
