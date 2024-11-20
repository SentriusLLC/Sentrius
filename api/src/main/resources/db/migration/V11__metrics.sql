CREATE TABLE terminal_session_metadata (
                                           id BIGSERIAL PRIMARY KEY,
                                           session_id BIGINT NOT NULL REFERENCES session_log(id) ON DELETE CASCADE,
                                           user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                           host_system_id BIGINT NOT NULL REFERENCES host_systems(host_system_id),
                                           start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                           end_time TIMESTAMP,
                                           ip_address VARCHAR(45),
                                           session_status VARCHAR(50) DEFAULT 'ACTIVE', -- e.g., ACTIVE, CLOSED, INTERRUPTED
                                           is_suspicious BOOLEAN DEFAULT FALSE
);


CREATE TABLE user_experience_metrics (
                                         id BIGSERIAL PRIMARY KEY,
                                         user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                         session_id BIGINT NOT NULL REFERENCES terminal_session_metadata(id) ON DELETE CASCADE,
                                         command_diversity INTEGER DEFAULT 0, -- Number of unique command categories used
                                         advanced_tool_usage BOOLEAN DEFAULT FALSE, -- Use of tools like awk, sed, grep
                                         error_resolution_count INTEGER DEFAULT 0, -- Number of successfully resolved errors
                                         manual_pages_usage_count INTEGER DEFAULT 0, -- Number of times man/help was used
                                         FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE terminal_risk_indicators (
                                          id BIGSERIAL PRIMARY KEY,
                                          session_id BIGINT NOT NULL REFERENCES terminal_session_metadata(id) ON DELETE CASCADE,
                                          dangerous_commands_count INTEGER DEFAULT 0, -- e.g., "rm -rf"
                                          unauthorized_access_attempts INTEGER DEFAULT 0, -- Access to restricted files/directories
                                          geo_anomaly BOOLEAN DEFAULT FALSE, -- Access from unusual locations
                                          out_of_hours BOOLEAN DEFAULT FALSE -- Sessions outside expected working hours
);
CREATE TABLE terminal_behavior_metrics (
                                           id BIGSERIAL PRIMARY KEY,
                                           session_id BIGINT NOT NULL REFERENCES terminal_session_metadata(id) ON DELETE CASCADE,
                                           total_commands INTEGER DEFAULT 0, -- Total number of commands issued
                                           unique_commands INTEGER DEFAULT 0, -- Number of unique commands
                                           avg_command_length FLOAT, -- Average command length in characters
                                           sudo_usage_count INTEGER DEFAULT 0, -- Number of privileged commands used
                                           max_idle_time INTERVAL, -- Longest idle period between commands
                                           FOREIGN KEY (session_id) REFERENCES terminal_session_metadata(id) ON DELETE CASCADE
);

CREATE TABLE terminal_commands (
                                   id BIGSERIAL PRIMARY KEY,
                                   session_id BIGINT NOT NULL REFERENCES terminal_session_metadata(id) ON DELETE CASCADE,
                                   command TEXT NOT NULL, -- Full command issued
                                   command_category VARCHAR(255), -- e.g., file_management, networking
                                   execution_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- When the command was issued
                                   execution_status VARCHAR(50) DEFAULT 'SUCCESS', -- e.g., SUCCESS, FAILED
                                   output TEXT, -- Optional: store command output
                                   FOREIGN KEY (session_id) REFERENCES terminal_session_metadata(id) ON DELETE CASCADE
);



CREATE TABLE analytics_tracking (
                                    id BIGSERIAL PRIMARY KEY,
                                    session_id BIGINT NOT NULL UNIQUE,
                                    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    status VARCHAR(50) DEFAULT 'PROCESSED' -- Options: 'PENDING', 'PROCESSING', 'PROCESSED'
);
