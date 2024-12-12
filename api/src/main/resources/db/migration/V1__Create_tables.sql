-- Create usertypes table
CREATE TABLE usertypes (
                           id BIGSERIAL PRIMARY KEY,
                           user_type_name VARCHAR(255) UNIQUE NOT NULL,
                           automation_access VARCHAR(255) DEFAULT 'CAN_VIEW_AUTOMATION',
                           system_access VARCHAR(255) DEFAULT 'CAN_VIEW_SYSTEMS',
                           rule_access VARCHAR(255) DEFAULT 'CAN_VIEW_RULES',
                           user_access VARCHAR(255) DEFAULT 'CAN_VIEW_USERS',
                           ztat_access VARCHAR(255) DEFAULT 'CAN_VIEW_ZTATS',
                           application_access VARCHAR(255) DEFAULT 'CAN_LOG_IN'
);

CREATE TABLE user_access_set (
                                 user_type_id BIGINT NOT NULL REFERENCES usertypes(id),
                                 access_set VARCHAR(255)
);

CREATE TABLE user_special_control_set (
                                          user_type_id BIGINT NOT NULL REFERENCES usertypes(id),
                                          special_control_set VARCHAR(255)
);

-- Create users table
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       username VARCHAR(255) UNIQUE NOT NULL,
                       name VARCHAR(255),
                       password VARCHAR(255) NOT NULL,
                       email_address VARCHAR(255),
                       image_url VARCHAR(255),
                       role_id BIGINT,
                       team VARCHAR(255),
                       FOREIGN KEY (role_id) REFERENCES usertypes(id)
);

CREATE TABLE host_groups (
                             id BIGSERIAL PRIMARY KEY,
                             name VARCHAR(255),
                             description VARCHAR(255),
                             configuration TEXT
);

CREATE TABLE user_hostgroups (
                                 user_id BIGINT NOT NULL REFERENCES users(id),
                                 hostgroup_id BIGINT NOT NULL REFERENCES host_groups(id),
                                 PRIMARY KEY (user_id, hostgroup_id)
);

CREATE TABLE host_systems (
                              host_system_id BIGSERIAL PRIMARY KEY,
                              display_name VARCHAR(255),
                              ssh_user VARCHAR(255) NOT NULL DEFAULT 'root',
                              ssh_user_password VARCHAR(255),
                              host VARCHAR(255),
                              port INTEGER DEFAULT 22,
                              display_label VARCHAR(255),
                              authorized_keys VARCHAR(255) DEFAULT '~/.ssh/authorized_keys',
                              checked BOOLEAN DEFAULT FALSE,
                              status_code VARCHAR(255) DEFAULT 'INITIAL_STATUS',
                              error_message VARCHAR(255),
                              instance_id INTEGER,
                              locked BOOLEAN DEFAULT FALSE
);

CREATE TABLE host_system_public_keys (
                                         host_system_id BIGINT NOT NULL REFERENCES host_systems(host_system_id),
                                         public_key VARCHAR(2048),
                                         PRIMARY KEY (host_system_id, public_key)
);

CREATE TABLE proxy_hosts (
                             id BIGSERIAL PRIMARY KEY,
                             host VARCHAR(255),
                             port INTEGER,
                             host_system_id BIGINT NOT NULL REFERENCES host_systems(host_system_id),
                             error_message VARCHAR(255)
);

CREATE TABLE time_config (
                             time_config_id BIGSERIAL PRIMARY KEY,
                             uuid VARCHAR(255) NOT NULL UNIQUE,
                             title VARCHAR(255),
                             configuration TEXT
);

CREATE TABLE time_configs (
                              id BIGSERIAL PRIMARY KEY,
                              time_config_id BIGINT NOT NULL REFERENCES time_config(time_config_id)
);

CREATE TABLE hostgroup_hostsystems (
                                       hostgroup_id BIGINT NOT NULL REFERENCES host_groups(id),
                                       host_system_id BIGINT NOT NULL REFERENCES host_systems(host_system_id),
                                       PRIMARY KEY (hostgroup_id, host_system_id)
);

CREATE TABLE IF NOT EXISTS error_output (
                                            id BIGSERIAL PRIMARY KEY,
                                            system_id INTEGER,
                                            error_type VARCHAR NOT NULL,
                                            error_location VARCHAR,
                                            error_hash VARCHAR NOT NULL,
                                            error_logs TEXT NOT NULL,
                                            log_tm TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS session_log (
                                           id BIGSERIAL PRIMARY KEY,
                                           session_tm TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                           first_name VARCHAR,
                                           last_name VARCHAR,
                                           username VARCHAR NOT NULL,
                                           ip_address VARCHAR
);

-- Rules and zero trust
CREATE TABLE IF NOT EXISTS rules (
                                     id BIGSERIAL PRIMARY KEY,
                                     rule_name VARCHAR,
                                     rule_class VARCHAR,
                                     rule_config VARCHAR
);

CREATE TABLE IF NOT EXISTS system_rules (
                                            system_id INTEGER NOT NULL,
                                            rule_id INTEGER NOT NULL,
                                            PRIMARY KEY (system_id, rule_id),
    CONSTRAINT fk_system FOREIGN KEY (system_id) REFERENCES host_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_rule FOREIGN KEY (rule_id) REFERENCES rules(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS terminal_log (
                                            id BIGSERIAL PRIMARY KEY,
                                            session_id BIGINT,
                                            instance_id INTEGER,
                                            output TEXT NOT NULL,
                                            log_tm TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                            display_nm VARCHAR NOT NULL,
                                            username VARCHAR NOT NULL,
                                            host VARCHAR NOT NULL,
                                            port INTEGER NOT NULL,
                                            FOREIGN KEY (session_id) REFERENCES session_log(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS ztat_reasons (
                                            id BIGSERIAL PRIMARY KEY,
                                            command_need VARCHAR NOT NULL,
                                            reason_identifier VARCHAR,
                                            url VARCHAR
);

CREATE TABLE IF NOT EXISTS ztat_requests (
                                             id BIGSERIAL PRIMARY KEY,
                                             user_id BIGINT,
                                             system_id BIGINT,
                                             command VARCHAR NOT NULL,
                                             command_hash VARCHAR NOT NULL,
                                             ztat_reason_id BIGINT,
                                             last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                             FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (ztat_reason_id) REFERENCES ztat_reasons(id),
    FOREIGN KEY (system_id) REFERENCES host_systems(host_system_id)
    );

CREATE TABLE IF NOT EXISTS ztat_approvals (
                                              id BIGSERIAL PRIMARY KEY,
                                              approver_id BIGINT,
                                              ztat_request_id BIGINT,
                                              uses INTEGER,
                                              approved BOOLEAN NOT NULL DEFAULT FALSE,
                                              last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                              FOREIGN KEY (approver_id) REFERENCES users(id),
    FOREIGN KEY (ztat_request_id) REFERENCES ztat_requests(id)
    );

CREATE TABLE IF NOT EXISTS notifications (
                                             id BIGSERIAL PRIMARY KEY,
                                             notification_type INTEGER DEFAULT 0,
                                             notification_reference VARCHAR(2048),
    initiator BIGINT,
    message TEXT,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (initiator) REFERENCES users(id)
    );

CREATE TABLE IF NOT EXISTS notification_recipients (
                                                       notification_id BIGINT REFERENCES notifications(id),
    user_id BIGINT REFERENCES users(id),
    acted BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (notification_id, user_id)
    );

ALTER TABLE session_log ADD COLUMN IF NOT EXISTS closed BOOLEAN DEFAULT FALSE;

-- Automation Table
CREATE TABLE automation (
                            id BIGSERIAL PRIMARY KEY,
                            user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            script_type VARCHAR(255),
                            display_nm VARCHAR(255) NOT NULL,
                            script TEXT NOT NULL,
                            description VARCHAR(255),
                            type VARCHAR(255),
                            state VARCHAR(255),
                            automation_options TEXT
);

-- Automation Shares Table
CREATE TABLE automation_shares (
                                   id BIGSERIAL PRIMARY KEY,
                                   automation_id BIGINT NOT NULL REFERENCES automation(id) ON DELETE CASCADE,
                                   user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

-- Automation Assignments Table
CREATE TABLE automation_assignments (
                                        id BIGSERIAL PRIMARY KEY,
                                        automation_id BIGINT NOT NULL REFERENCES automation(id) ON DELETE CASCADE,
                                        system_id BIGINT NOT NULL REFERENCES host_systems(host_system_id),
                                        number_execs INTEGER
);

-- Automation Cron Entries Table
CREATE TABLE automation_cron_entries (
                                         id BIGSERIAL PRIMARY KEY,
                                         automation_id BIGINT NOT NULL REFERENCES automation(id) ON DELETE CASCADE,
                                         script_cron VARCHAR(255),
                                         FOREIGN KEY (automation_id) REFERENCES automation(id) ON DELETE CASCADE
);

-- Automation Executions Table
CREATE TABLE automation_executions (
                                       id BIGSERIAL PRIMARY KEY,
                                       system_id BIGINT NOT NULL REFERENCES host_systems(host_system_id),
                                       automation_id BIGINT NOT NULL REFERENCES automation(id) ON DELETE CASCADE,
                                       execution_output TEXT,
                                       log_tm TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_public_keys (
                                                id BIGSERIAL PRIMARY KEY,
                                                key_name VARCHAR NOT NULL,
                                                key_type VARCHAR,
                                                fingerprint VARCHAR,
                                                public_key TEXT NOT NULL,
                                                is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                user_id BIGINT,
                                                group_id BIGINT,
                                                FOREIGN KEY (group_id) REFERENCES host_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS application_key (
                                               id BIGSERIAL PRIMARY KEY,
                                               public_key TEXT NOT NULL,
                                               private_key TEXT NOT NULL,
                                               passphrase VARCHAR
);

CREATE TABLE IF NOT EXISTS operations_request (
                                                  id BIGSERIAL PRIMARY KEY,
                                                  user_id BIGINT,
                                                  command VARCHAR NOT NULL,
                                                  command_hash VARCHAR NOT NULL,
                                                  ztat_reason_id BIGINT,
                                                  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                  FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (ztat_reason_id) REFERENCES ztat_reasons(id)
    );

CREATE TABLE IF NOT EXISTS ops_approvals (
                                             id BIGSERIAL PRIMARY KEY,
                                             approver_id BIGINT,
                                             ztat_request_id BIGINT,
                                             uses INTEGER,
                                             approved BOOLEAN NOT NULL DEFAULT FALSE,
                                             last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                             FOREIGN KEY (approver_id) REFERENCES users(id),
    FOREIGN KEY (ztat_request_id) REFERENCES operations_request(id)
    );

CREATE TABLE IF NOT EXISTS integration_security_tokens (
                                                           id BIGSERIAL PRIMARY KEY,
                                                           name VARCHAR(255) NOT NULL,
    connection_info TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    type VARCHAR(100) NOT NULL DEFAULT 'UNKNOWN'
    );

CREATE TABLE IF NOT EXISTS user_settings (
                                             user_id BIGINT PRIMARY KEY,
                                             bg VARCHAR(7),
    fg VARCHAR(7),
    d1 VARCHAR(7),
    d2 VARCHAR(7),
    d3 VARCHAR(7),
    d4 VARCHAR(7),
    d5 VARCHAR(7),
    d6 VARCHAR(7),
    d7 VARCHAR(7),
    d8 VARCHAR(7),
    b1 VARCHAR(7),
    b2 VARCHAR(7),
    b3 VARCHAR(7),
    b4 VARCHAR(7),
    b5 VARCHAR(7),
    b6 VARCHAR(7),
    b7 VARCHAR(7),
    b8 VARCHAR(7),
    json_config TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );
