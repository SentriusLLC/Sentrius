CREATE TABLE agent_communications (
      id BIGSERIAL PRIMARY KEY,
      source_agent VARCHAR(255) NOT NULL,
      target_agent VARCHAR(255) NOT NULL,
      message_type VARCHAR(100) NOT NULL,
      payload TEXT NOT NULL, -- fulltext message
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
