-- Link ztat_requests and operations_request to agent_communications
CREATE TABLE IF NOT EXISTS request_communication_links (
   id BIGSERIAL PRIMARY KEY,
   ztat_request_id BIGINT,
   operations_request_id BIGINT,
   communication_id BIGINT NOT NULL,
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

   FOREIGN KEY (ztat_request_id) REFERENCES ztat_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (operations_request_id) REFERENCES operations_request(id) ON DELETE CASCADE,
    FOREIGN KEY (communication_id) REFERENCES agent_communications(id) ON DELETE CASCADE
    );

-- Enforce uniqueness constraints to avoid duplicate mappings
-- 1 ZTAT request can only link to a specific communication once
ALTER TABLE request_communication_links
    ADD CONSTRAINT uq_ztat_comm UNIQUE (ztat_request_id, communication_id);

-- 1 operations request can only link to a specific communication once
ALTER TABLE request_communication_links
    ADD CONSTRAINT uq_op_comm UNIQUE (operations_request_id, communication_id);

-- Optional: prevent both ztat_request_id and operations_request_id from being NULL at the same time
ALTER TABLE request_communication_links
    ADD CONSTRAINT chk_at_least_one_request CHECK (
        ztat_request_id IS NOT NULL OR operations_request_id IS NOT NULL
        );