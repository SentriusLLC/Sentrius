ALTER TABLE operations_request
DROP CONSTRAINT operations_request_user_id_fkey;
ALTER TABLE operations_request
    ADD CONSTRAINT operations_request_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id)
            ON DELETE CASCADE;
