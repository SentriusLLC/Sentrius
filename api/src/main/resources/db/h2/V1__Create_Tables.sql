CREATE TABLE automation_executions (
                                       id BIGSERIAL PRIMARY KEY,
                                       system_id BIGINT NOT NULL REFERENCES host_systems(host_system_id),
                                       automation_id BIGINT NOT NULL REFERENCES automation(id) ON DELETE CASCADE,
                                       executed_by_user_id BIGINT REFERENCES users(id),
                                       execution_output TEXT,
                                       status VARCHAR(50) DEFAULT 'SUCCESS',
                                       exit_code INTEGER,
                                       log_tm TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
