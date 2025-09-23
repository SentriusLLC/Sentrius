-- V28__create_ssh_session_summary_table.sql
-- Create table for SSH/Terminal session summaries

-- Table to store AI-generated SSH/Terminal session summaries
CREATE TABLE ssh_session_summaries (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL UNIQUE,
    user_identifier VARCHAR(255) NOT NULL,
    target_identifier VARCHAR(255),
    session_start TIMESTAMP,
    session_end TIMESTAMP,
    summary TEXT,
    commands_executed TEXT,
    key_activities TEXT,
    risk_indicators TEXT,
    terminal_log_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    
    CONSTRAINT fk_ssh_session_summaries_session
        FOREIGN KEY (session_id) REFERENCES session_log(id)
        ON DELETE CASCADE
);

-- Create unique index on session_id
CREATE UNIQUE INDEX idx_ssh_session_summaries_session_id ON ssh_session_summaries(session_id);

-- Create index on user_identifier for queries
CREATE INDEX idx_ssh_session_summaries_user ON ssh_session_summaries(user_identifier);

-- Create index on created_at for time-based queries
CREATE INDEX idx_ssh_session_summaries_created_at ON ssh_session_summaries(created_at);
