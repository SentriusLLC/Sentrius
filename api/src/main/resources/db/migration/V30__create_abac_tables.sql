-- Create ABAC attribute definitions table
CREATE TABLE IF NOT EXISTS attribute_definitions (
    id BIGSERIAL PRIMARY KEY,
    attribute_name VARCHAR(255) NOT NULL,
    attribute_scope VARCHAR(50) NOT NULL CHECK (attribute_scope IN ('SUBJECT', 'RESOURCE', 'ACTION', 'ENVIRONMENT')),
    attribute_type VARCHAR(50) NOT NULL CHECK (attribute_type IN ('STRING', 'INTEGER', 'BOOLEAN', 'DATE', 'TIME', 'DATETIME', 'JSON', 'LIST', 'SET')),
    description TEXT,
    validation_schema TEXT,
    allowed_values TEXT,
    synced_with_keycloak BOOLEAN DEFAULT false,
    keycloak_attribute_name VARCHAR(255),
    is_required BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    UNIQUE (attribute_name, attribute_scope)
);

CREATE INDEX IF NOT EXISTS idx_attr_def_scope_active ON attribute_definitions(attribute_scope, is_active);
CREATE INDEX IF NOT EXISTS idx_attr_def_keycloak ON attribute_definitions(synced_with_keycloak) WHERE synced_with_keycloak = true;

-- Create ABAC attribute assignments table
CREATE TABLE IF NOT EXISTS attribute_assignments (
    id BIGSERIAL PRIMARY KEY,
    attribute_definition_id BIGINT NOT NULL REFERENCES attribute_definitions(id) ON DELETE CASCADE,
    target_type VARCHAR(50) NOT NULL CHECK (target_type IN ('USER', 'ROLE', 'GROUP', 'ENDPOINT', 'DATA_ENTITY', 'OPERATION', 'SYSTEM')),
    target_id VARCHAR(500) NOT NULL,
    attribute_value TEXT NOT NULL,
    source VARCHAR(50) DEFAULT 'SENTRIUS' CHECK (source IN ('KEYCLOAK', 'SENTRIUS', 'LDAP', 'EXTERNAL', 'POLICY')),
    synced_from_keycloak BOOLEAN DEFAULT false,
    priority INTEGER DEFAULT 0,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_attr_assign_target ON attribute_assignments(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_attr_assign_definition ON attribute_assignments(attribute_definition_id);
CREATE INDEX IF NOT EXISTS idx_attr_assign_active ON attribute_assignments(is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_attr_assign_valid ON attribute_assignments(valid_from, valid_until);

-- Create ABAC access policies table
CREATE TABLE IF NOT EXISTS access_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    resource_type VARCHAR(50) NOT NULL CHECK (resource_type IN ('ENDPOINT', 'DATA_ENTITY', 'OPERATION', 'SYSTEM_RESOURCE')),
    resource_pattern VARCHAR(500) NOT NULL,
    actions VARCHAR(500),
    effect VARCHAR(20) NOT NULL DEFAULT 'ALLOW' CHECK (effect IN ('ALLOW', 'DENY')),
    priority INTEGER DEFAULT 0,
    rule_combination VARCHAR(20) DEFAULT 'AND' CHECK (rule_combination IN ('AND', 'OR')),
    rules_json TEXT,
    is_active BOOLEAN DEFAULT true,
    evaluation_mode VARCHAR(20) DEFAULT 'STRICT' CHECK (evaluation_mode IN ('STRICT', 'PERMISSIVE', 'AUDIT_ONLY')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_policy_resource_type ON access_policies(resource_type, is_active);
CREATE INDEX IF NOT EXISTS idx_policy_priority ON access_policies(priority DESC) WHERE is_active = true;

-- Create ABAC policy rules table
CREATE TABLE IF NOT EXISTS policy_rules (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES access_policies(id) ON DELETE CASCADE,
    attribute_definition_id BIGINT NOT NULL REFERENCES attribute_definitions(id) ON DELETE RESTRICT,
    operator VARCHAR(50) NOT NULL CHECK (operator IN (
        'EQUALS', 'NOT_EQUALS', 'CONTAINS', 'STARTS_WITH', 'ENDS_WITH', 
        'REGEX_MATCH', 'GREATER_THAN', 'LESS_THAN', 'GREATER_OR_EQUAL', 
        'LESS_OR_EQUAL', 'IN_LIST', 'NOT_IN_LIST', 'IS_NULL', 'IS_NOT_NULL'
    )),
    expected_value TEXT NOT NULL,
    is_negated BOOLEAN DEFAULT false,
    evaluation_order INTEGER DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_policy_rule_policy ON policy_rules(policy_id);
CREATE INDEX IF NOT EXISTS idx_policy_rule_order ON policy_rules(policy_id, evaluation_order, is_active);

-- Insert example attribute definitions
INSERT INTO attribute_definitions (attribute_name, attribute_scope, attribute_type, description, synced_with_keycloak, is_active)
VALUES 
    ('department', 'SUBJECT', 'STRING', 'User department', true, true),
    ('clearance_level', 'SUBJECT', 'STRING', 'Security clearance level', true, true),
    ('data_sensitivity', 'RESOURCE', 'STRING', 'Data sensitivity classification', false, true),
    ('http_method', 'ACTION', 'STRING', 'HTTP request method', false, true),
    ('time_of_day', 'ENVIRONMENT', 'TIME', 'Time of day for access', false, true)
ON CONFLICT (attribute_name, attribute_scope) DO NOTHING;
