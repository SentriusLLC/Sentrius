create table if not exists operations_request (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, command varchar not null, command_hash varchar not null, jit_reason_id BIGINT, last_updated timestamp default CURRENT_TIMESTAMP,  foreign key (user_id) references users(id), foreign key (jit_reason_id) references jit_reasons(id));

create table if not exists ops_approvals (id BIGINT PRIMARY KEY AUTO_INCREMENT, approver_id BIGINT, jit_request_id BIGINT, uses INTEGER, approved boolean not null default false, last_updated timestamp default CURRENT_TIMESTAMP,  foreign key (approver_id) references users(id), foreign key (jit_request_id) references operations_request(id));

create table if not exists profile_rules (id SERIAL PRIMARY KEY,profile_id INTEGER, rule_id INTEGER, foreign key (profile_id) references profiles(id), foreign key (rule_id) references rules(id));

create table if not exists proxy_log (id BIGINT PRIMARY KEY AUTO_INCREMENT, session_tm timestamp default CURRENT_TIMESTAMP, host_proxy_id INTEGER, username varchar not null, ip_address varchar,  foreign key (host_proxy_id) references host_proxies(id));