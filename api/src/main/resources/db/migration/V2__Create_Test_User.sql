-- Insert a UserType with full access (e.g., a "System Admin")
INSERT INTO usertypes (id, user_type_name, automation_access, system_access, rule_access, user_access, ztat_access, application_access)
VALUES (-1, 'Application Admin', 'CAN_RUN_AUTOMATION', 'CAN_MANAGE_SYSTEMS', 'CAN_VIEW_RULES', 'CAN_MANAGE_USERS',
        'CAN_VIEW_ZTATS',
        'CAN_MANAGE_APPLICATION');

-- Insert a test user and associate with the UserType created above
INSERT INTO users (id, username, name, password, email_address, image_url, role_id, team)
VALUES (-1, 'admin', 'Test User', '$2a$10$LcIvlLX3vchavg8I.VmDLeWIoVETLJM7yK0y8qwn5e0v9QwfcakK6',
        'testuser@example.com', 'https://example.com/image.jpg', -1, 'Test Team');


-- Insert a host group for the test user

-- Insert default host group "Default Host Group" for "Test User"
INSERT INTO host_groups (id, name, description, configuration)
VALUES (-1, 'Default Host Group', 'Default host group for Test User', 'Default configuration');

-- Assign "Test User" to "Default Host Group"
INSERT INTO user_hostgroups (user_id, hostgroup_id)
VALUES (-1, -1);
