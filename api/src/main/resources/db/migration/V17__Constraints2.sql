ALTER TABLE ops_uses
DROP CONSTRAINT ops_uses_ops_approval_id_fkey;

ALTER TABLE ops_uses
    ADD CONSTRAINT ops_uses_ops_approval_id_fkey
        FOREIGN KEY (ops_approval_id) REFERENCES ops_approvals(id) ON DELETE CASCADE;
