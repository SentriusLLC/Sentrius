CREATE TABLE agent_heartbeats (
      id BIGSERIAL PRIMARY KEY,
      agent_id VARCHAR(255) NOT NULL,
      last_heartbeat TIMESTAMP NOT NULL,
      status VARCHAR(50) NOT NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
