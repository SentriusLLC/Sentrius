CREATE TABLE IF NOT EXISTS chat_log (
        id BIGSERIAL PRIMARY KEY,
        session_id BIGINT NOT NULL,
        chat_group_id VARCHAR NOT NULL, -- Unique identifier for different chat dialogs within the session
        instance_id INTEGER,
        sender VARCHAR NOT NULL, -- username or system (e.g., AI agent)
        message TEXT NOT NULL,
        message_tm TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (session_id) REFERENCES session_log(id) ON DELETE CASCADE
    );
