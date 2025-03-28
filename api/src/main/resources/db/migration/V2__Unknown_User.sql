INSERT INTO usertypes (id, user_type_name, automation_access, system_access, rule_access, user_access, ztat_access,
application_access) VALUES (-3, 'Unknown User', 'CAN_VIEW_AUTOMATION', 'CANNOT_VIEW_SYSTEMS', 'CANNOT_VIEW_RULES',
                            'NOT_AUTHORIZED_USER',
                   'CAN_REQUEST_ZTAT',
                   'CANNOT_LOG_IN');

CREATE TABLE atpl_policies (
                               id UUID PRIMARY KEY,
                               policy_id TEXT NOT NULL,
                               version TEXT NOT NULL,
                               description TEXT,
                               yaml TEXT NOT NULL,
                               active BOOLEAN DEFAULT TRUE,
                               created_at TIMESTAMP,
                               updated_at TIMESTAMP
);


CREATE TABLE agent_policy_assignments (
                                          user_id BIGINT NOT NULL,
                                          policy_id UUID NOT NULL,
                                          assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                          PRIMARY KEY (user_id, policy_id),

                                          FOREIGN KEY (user_id) REFERENCES users(id),
                                          FOREIGN KEY (policy_id) REFERENCES atpl_policies(id)
);

ALTER TABLE users
    ADD COLUMN identity_type VARCHAR(255) NOT NULL DEFAULT 'USER',
ADD CONSTRAINT check_identity_type CHECK (identity_type IN ('USER','NON_PERSON_ENTITY'));

ALTER TABLE ztat_approvals ADD COLUMN token UUID UNIQUE NOT NULL;