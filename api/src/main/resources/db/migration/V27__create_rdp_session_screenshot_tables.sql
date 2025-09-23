-- V27__create_rdp_session_screenshot_tables.sql
-- Create tables for RDP session screenshot capture and summarization

-- Table to store RDP session screenshot image data
CREATE TABLE rdp_session_screenshots (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    captured_at TIMESTAMP NOT NULL,
    image_data BYTEA NOT NULL,
    image_format VARCHAR(10),
    file_size BIGINT,
    processed BOOLEAN DEFAULT FALSE,
    analysis_result TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for efficient querying
CREATE INDEX idx_rdp_session_screenshots_session_id ON rdp_session_screenshots(session_id);
CREATE INDEX idx_rdp_session_screenshots_processed ON rdp_session_screenshots(processed);
CREATE INDEX idx_rdp_session_screenshots_captured_at ON rdp_session_screenshots(captured_at);

-- Table to store AI-generated RDP session summaries
CREATE TABLE rdp_session_summaries (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    user_identifier VARCHAR(255) NOT NULL,
    target_identifier VARCHAR(255),
    session_start TIMESTAMP,
    session_end TIMESTAMP,
    summary TEXT,
    key_activities TEXT,
    risk_indicators TEXT,
    screenshot_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create unique index on session_id
CREATE UNIQUE INDEX idx_rdp_session_summaries_session_id ON rdp_session_summaries(session_id);
