
INSERT INTO usertypes (id, user_type_name, automation_access, system_access, rule_access, user_access, ztat_access,
                       application_access) VALUES (-2, 'System Admin', 'CAN_RUN_AUTOMATION', 'CAN_MANAGE_SYSTEMS', 'CAN_VIEW_RULES', 'CAN_MANAGE_USERS',
        'CAN_VIEW_ZTATS',
        'CAN_MANAGE_APPLICATION');

INSERT INTO usertypes (id, user_type_name, automation_access, system_access, rule_access, user_access, ztat_access,
                       application_access) VALUES (-4, 'Base User', 'CAN_RUN_AUTOMATION', 'CAN_MANAGE_SYSTEMS', 'CAN_VIEW_RULES', 'CAN_MANAGE_USERS',
        'CAN_VIEW_ZTATS',
        'CAN_MANAGE_APPLICATION');