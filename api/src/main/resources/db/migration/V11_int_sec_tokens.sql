-- Flyway migration script to create the integration_security_tokens table

CREATE TABLE integration_security_tokens (
     id BIGSERIAL IDENTITY PRIMARY KEY, -- Auto-incrementing ID
     name VARCHAR(255) NOT NULL,                        -- Name of the integration
     connection_info CLOB NOT NULL,                     -- JSON column to store connection details
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,    -- Timestamp for record creation
     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP -- Timestamp for record updates
);
