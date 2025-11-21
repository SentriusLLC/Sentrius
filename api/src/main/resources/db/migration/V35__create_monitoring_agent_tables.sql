-- V35__create_monitoring_agent_tables.sql
-- Database schema for Monitoring Agent configuration and metrics

-- Agent monitoring configuration table
CREATE TABLE IF NOT EXISTS agent_monitoring_config (
    id BIGSERIAL PRIMARY KEY,
    endpoint_url VARCHAR(500) NOT NULL,
    service_name VARCHAR(255),
    response_time_threshold BIGINT,
    error_rate_threshold DOUBLE PRECISION,
    latency_threshold DOUBLE PRECISION,
    analysis_window_minutes INTEGER DEFAULT 5,
    wait_for_trend BOOLEAN DEFAULT FALSE,
    notify_on_down BOOLEAN DEFAULT TRUE,
    notify_on_slow_response BOOLEAN DEFAULT FALSE,
    notify_on_high_errors BOOLEAN DEFAULT TRUE,
    notify_on_high_latency BOOLEAN DEFAULT FALSE,
    notification_channels TEXT,
    use_ai_evaluation BOOLEAN DEFAULT FALSE,
    stability_evaluation_prompt TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(endpoint_url)
);

-- Endpoint health metrics table
CREATE TABLE IF NOT EXISTS endpoint_health_metrics (
    id BIGSERIAL PRIMARY KEY,
    endpoint_url VARCHAR(500) NOT NULL,
    status VARCHAR(50) NOT NULL,
    response_time BIGINT,
    error_rate DOUBLE PRECISION,
    avg_latency DOUBLE PRECISION,
    throughput DOUBLE PRECISION,
    last_error TEXT,
    checked_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_endpoint_url ON endpoint_health_metrics(endpoint_url);
CREATE INDEX IF NOT EXISTS idx_checked_at ON endpoint_health_metrics(checked_at);

-- Notification history table
CREATE TABLE IF NOT EXISTS notification_history (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    endpoint_url VARCHAR(500),
    sent_at TIMESTAMP NOT NULL,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by VARCHAR(255),
    acknowledged_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sent_at ON notification_history(sent_at);
CREATE INDEX IF NOT EXISTS idx_severity ON notification_history(severity);
CREATE INDEX IF NOT EXISTS idx_acknowledged ON notification_history(acknowledged);

-- Notification channel configuration table
CREATE TABLE IF NOT EXISTS notification_channel_config (
    id BIGSERIAL PRIMARY KEY,
    channel_name VARCHAR(50) NOT NULL,
    channel_type VARCHAR(50) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    configuration JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(channel_name)
);

-- Insert default notification channels
INSERT INTO notification_channel_config (channel_name, channel_type, enabled, configuration) VALUES
    ('INTERNAL', 'INTERNAL', TRUE, '{"retention_days": 30}'::jsonb),
    ('JIRA', 'EXTERNAL', FALSE, '{"url": "", "project_key": "", "api_token": ""}'::jsonb),
    ('PAGERDUTY', 'EXTERNAL', FALSE, '{"integration_key": ""}'::jsonb),
    ('SLACK', 'EXTERNAL', FALSE, '{"webhook_url": ""}'::jsonb),
    ('EMAIL', 'EXTERNAL', FALSE, '{"smtp_host": "", "smtp_port": 587, "from_address": ""}'::jsonb)
ON CONFLICT (channel_name) DO NOTHING;
