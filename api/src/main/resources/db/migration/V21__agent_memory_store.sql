-- Create agent memory store with markings support
CREATE TABLE agent_memory (
    id BIGSERIAL PRIMARY KEY,
    memory_key VARCHAR(255) NOT NULL,
    memory_value TEXT NOT NULL,
    memory_type VARCHAR(50) DEFAULT 'JSON',
    agent_id VARCHAR(255),
    agent_name VARCHAR(255),
    conversation_id VARCHAR(255),
    classification VARCHAR(50) DEFAULT 'PRIVATE',
    markings VARCHAR(255),
    access_level VARCHAR(50) DEFAULT 'AGENT_ONLY',
    creator_user_id VARCHAR(255),
    creator_user_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    shared_with_agents TEXT,
    metadata JSONB,
    version INTEGER DEFAULT 1
);

-- Create index for efficient memory retrieval
CREATE INDEX idx_agent_memory_agent_id ON agent_memory(agent_id);
CREATE INDEX idx_agent_memory_conversation_id ON agent_memory(conversation_id);
CREATE INDEX idx_agent_memory_classification ON agent_memory(classification);
CREATE INDEX idx_agent_memory_access_level ON agent_memory(access_level);
CREATE INDEX idx_agent_memory_markings ON agent_memory(markings);
CREATE INDEX idx_agent_memory_creator ON agent_memory(creator_user_id);

-- Create enhanced user attributes table for ABAC
CREATE TABLE user_attributes (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    attribute_name VARCHAR(255) NOT NULL,
    attribute_value TEXT NOT NULL,
    attribute_type VARCHAR(50) DEFAULT 'STRING',
    source VARCHAR(50) DEFAULT 'SENTRIUS',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    synced_from_keycloak BOOLEAN DEFAULT false,
    UNIQUE(user_id, attribute_name)
);

-- Create index for user attributes
CREATE INDEX idx_user_attributes_user_id ON user_attributes(user_id);
CREATE INDEX idx_user_attributes_name ON user_attributes(attribute_name);
CREATE INDEX idx_user_attributes_active ON user_attributes(is_active);

-- Create memory access policies table for ABAC
CREATE TABLE memory_access_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_name VARCHAR(255) NOT NULL UNIQUE,
    policy_description TEXT,
    target_classification VARCHAR(50),
    target_markings VARCHAR(255),
    required_user_attributes JSONB,
    required_agent_attributes JSONB,
    access_type VARCHAR(50) DEFAULT 'READ',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for memory access policies
CREATE INDEX idx_memory_policies_classification ON memory_access_policies(target_classification);
CREATE INDEX idx_memory_policies_markings ON memory_access_policies(target_markings);
CREATE INDEX idx_memory_policies_active ON memory_access_policies(is_active);

-- Insert default memory access policies
INSERT INTO memory_access_policies (
    policy_name, 
    policy_description, 
    target_classification, 
    access_type, 
    required_user_attributes
) VALUES 
('PUBLIC_READ', 'Allow read access to public memory for all users', 'PUBLIC', 'READ', '{}'),
('PRIVATE_OWNER_ONLY', 'Allow full access to private memory only for creator', 'PRIVATE', 'FULL', '{"created_by": "user_id"}'),
('SHARED_TEAM_READ', 'Allow read access to shared memory for team members', 'SHARED', 'READ', '{"team": "required"}'),
('CONFIDENTIAL_ADMIN_ONLY', 'Allow access to confidential memory only for admins', 'CONFIDENTIAL', 'FULL', '{"user_type": "ADMIN"}');

-- Insert default classification examples
INSERT INTO agent_memory (
    memory_key,
    memory_value,
    memory_type,
    agent_id,
    classification,
    markings,
    access_level,
    creator_user_id,
    metadata
) VALUES 
('system.welcome_message', 
 '{"message": "Welcome to Sentrius Agent Memory Store", "version": "1.0"}',
 'JSON',
 'system',
 'PUBLIC',
 'SYSTEM,WELCOME',
 'ALL_USERS',
 'system',
 '{"is_system": true, "category": "documentation"}'
);