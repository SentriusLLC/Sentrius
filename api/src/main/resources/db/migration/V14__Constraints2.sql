ALTER TABLE agent_policy_assignments
DROP CONSTRAINT agent_policy_assignments_user_id_fkey;
ALTER TABLE agent_policy_assignments
    ADD CONSTRAINT agent_policy_assignments_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id)
            ON DELETE CASCADE;

