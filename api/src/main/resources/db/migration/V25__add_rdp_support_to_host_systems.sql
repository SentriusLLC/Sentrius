-- V25__add_rdp_support_to_host_systems.sql
-- Add RDP support fields to host_systems table

ALTER TABLE host_systems 
    ADD COLUMN rdp_enabled BOOLEAN DEFAULT FALSE,
    ADD COLUMN rdp_user VARCHAR(255) DEFAULT 'Administrator',
    ADD COLUMN rdp_password VARCHAR(255) DEFAULT '',
    ADD COLUMN rdp_port INTEGER DEFAULT 3389,
    ADD COLUMN rdp_domain VARCHAR(255) DEFAULT '';