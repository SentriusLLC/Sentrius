CREATE TABLE IF NOT EXISTS agent_feedback (
                                              id BIGSERIAL PRIMARY KEY,

                                              agent_id VARCHAR(255) NOT NULL,
    agent_name VARCHAR(255),

    feedback_type VARCHAR(50) NOT NULL,

    feedback_text TEXT NOT NULL,
    context TEXT,

    action_id VARCHAR(255),

    trust_impact INTEGER,

    provided_by VARCHAR(255) NOT NULL,

    timestamp TIMESTAMP NOT NULL,

    processed BOOLEAN NOT NULL DEFAULT FALSE,

    behavior_category VARCHAR(100),

    reinforcement_weight DOUBLE PRECISION
    );

-- Indexes
CREATE INDEX IF NOT EXISTS idx_agent_feedback_agent_id
    ON agent_feedback(agent_id);

CREATE INDEX IF NOT EXISTS idx_agent_feedback_timestamp
    ON agent_feedback(timestamp);

CREATE INDEX IF NOT EXISTS idx_agent_feedback_type
    ON agent_feedback(feedback_type);

CREATE INDEX IF NOT EXISTS idx_agent_feedback_processed
    ON agent_feedback(processed);
