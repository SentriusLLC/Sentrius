-- Create usertypes table
CREATE TABLE usertypes (
                           id BIGSERIAL PRIMARY KEY,
                           user_type_name VARCHAR(255) UNIQUE NOT NULL,
                           automation_access VARCHAR(255) DEFAULT 'CAN_VIEW_AUTOMATION',
                           system_access VARCHAR(255) DEFAULT 'CAN_VIEW_SYSTEMS',
                           rule_access VARCHAR(255) DEFAULT 'CAN_VIEW_RULES',
                           user_access VARCHAR(255) DEFAULT 'CAN_VIEW_USERS',
                           jit_access VARCHAR(255) DEFAULT 'CAN_VIEW_JITS',
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

create table if not exists error_output (
    id BIGSERIAL PRIMARY KEY,
    system_id INTEGER,
    error_type varchar not null,
    error_location varchar,
    error_hash varchar not null,
    error_logs TEXT not null,
    log_tm timestamp default CURRENT_TIMESTAMP);

create table if not exists session_log (
    id BIGSERIAL PRIMARY KEY,
    session_tm timestamp default CURRENT_TIMESTAMP,
    first_name varchar,
    last_name varchar,
    username varchar not null,
    ip_address varchar);


-- Rules and zero trust

create table if not exists rules (
        id BIGSERIAL PRIMARY KEY,
        ruleName varchar,
        ruleClass varchar,
        ruleConfig varchar);

create table if not exists system_rules (
    system_id INTEGER,
    rule_id INTEGER,
    primary key (system_id, rule_id),
    foreign key (system_id) references host_groups(id),
    foreign key (rule_id) references rules(id)
);

create table if not exists terminal_log (
    session_id BIGINT, instance_id INTEGER,
    output varchar not null,
    log_tm timestamp default CURRENT_TIMESTAMP,
    display_nm varchar not null,
    username varchar not null,
    host varchar not null,
    port INTEGER not null,
    foreign key (session_id) references session_log(id) on delete cascade);

create table if not exists jit_reasons (
    id BIGSERIAL PRIMARY KEY,
    command_need varchar not null,
    reason_identifier varchar, url varchar);

create table if not exists jit_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    system_id BIGINT,
    command varchar not null,
    command_hash varchar not null,
    jit_reason_id BIGINT,
    last_updated timestamp default CURRENT_TIMESTAMP,
    foreign key (user_id) references users(id),
    foreign key (jit_reason_id) references jit_reasons(id),
    foreign key (system_id) references host_systems(host_system_id));

create table if not exists jit_approvals (
    id BIGSERIAL PRIMARY KEY,
    approver_id BIGINT,
    jit_request_id BIGINT,
    uses INTEGER,
    approved boolean not null default false,
    last_updated timestamp default CURRENT_TIMESTAMP,
    foreign key (approver_id) references users(id),
    foreign key (jit_request_id) references jit_requests(id));


create table if not exists notifications (
                                             id BIGSERIAL PRIMARY KEY,
                                             notification_type INTEGER DEFAULT 0,
                                             notification_reference VARCHAR(2048),
                                             initiator BIGINT,
                                             message TEXT,
                                             last_updated timestamp default CURRENT_TIMESTAMP,  foreign key (initiator)
                                                 references users(id)
);


create table if not exists notification_recipients (
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
                            automation_options text
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
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            key_name VARCHAR NOT NULL,
            key_type VARCHAR,
            fingerprint VARCHAR,
            public_key text NOT NULL,
            is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            user_id BIGINT,
            group_id BIGINT,
            FOREIGN KEY (group_id) REFERENCES host_groups(id) ON DELETE CASCADE,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


create table if not exists application_key (id BIGSERIAL PRIMARY KEY, public_key TEXT not null,
                                            private_key TEXT not null, passphrase varchar);