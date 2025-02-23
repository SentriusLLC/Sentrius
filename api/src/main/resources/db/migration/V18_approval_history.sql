ALTER TABLE ztat_approvals ADD COLUMN rationale TEXT;
ALTER TABLE ops_approvals ADD COLUMN rationale TEXT;

CREATE TABLE IF NOT EXISTS ztat_uses (
                                         id BIGSERIAL PRIMARY KEY,
                                         ztat_approval_id BIGINT NOT NULL,
                                         user_id BIGINT NOT NULL,
                                         used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                         FOREIGN KEY (ztat_approval_id) REFERENCES ztat_approvals(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
    );


CREATE TABLE IF NOT EXISTS ops_uses (
                                         id BIGSERIAL PRIMARY KEY,
                                         ops_approval_id BIGINT NOT NULL,
                                         user_id BIGINT NOT NULL,
                                         used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                         FOREIGN KEY (ops_approval_id) REFERENCES ops_approvals(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
    );
