-- Flyway migration script to add the 'type' column to the integration_security_tokens table

ALTER TABLE integration_security_tokens
    ADD COLUMN type VARCHAR(100) NOT NULL DEFAULT 'UNKNOWN'; -- Add 'type' column with a default value

-- Optional: Update the 'type' column for existing rows, if applicable
-- UPDATE integration_security_tokens SET type = 'JIRA' WHERE name LIKE '%JIRA%';
-- UPDATE integration_security_tokens SET type = 'GitHub' WHERE name LIKE '%GitHub%';
