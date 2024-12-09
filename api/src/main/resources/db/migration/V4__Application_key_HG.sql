-- Add a column for application_key reference to host_groups
ALTER TABLE host_groups
    ADD COLUMN application_key_id BIGINT UNIQUE,
ADD CONSTRAINT fk_application_key FOREIGN KEY (application_key_id) REFERENCES application_key(id) ON DELETE CASCADE;
