ALTER TABLE ops_approvals
DROP CONSTRAINT ops_approvals_approver_id_fkey;
ALTER TABLE ops_approvals
    ADD CONSTRAINT ops_approvals_approver_id_fkey
        FOREIGN KEY (approver_id) REFERENCES users(id)
            ON DELETE CASCADE;
