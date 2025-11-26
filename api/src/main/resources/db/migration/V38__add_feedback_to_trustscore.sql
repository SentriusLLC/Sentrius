ALTER TABLE agent_trust_score_history
    ADD COLUMN IF NOT EXISTS feedback_score DOUBLE PRECISION;
