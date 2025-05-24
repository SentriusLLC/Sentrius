ALTER TABLE ops_approvals
    ADD CONSTRAINT ops_approvals_ztat_request_id_fkey
        FOREIGN KEY (ztat_request_id) REFERENCES operations_request(id)
            ON DELETE CASCADE;
