ALTER TABLE host_systems
    ADD COLUMN proxied_ssh_server BOOLEAN DEFAULT FALSE,
ADD COLUMN proxied_ssh_port INTEGER DEFAULT 0;