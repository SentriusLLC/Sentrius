-- Create agent_templates table for pre-configured agent templates
CREATE TABLE IF NOT EXISTS agent_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    agent_type VARCHAR(255) NOT NULL,
    icon VARCHAR(100),
    category VARCHAR(100),
    default_configuration TEXT,
    system_template BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER DEFAULT 0,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- Create index for faster lookups by enabled status and display order
CREATE INDEX IF NOT EXISTS idx_agent_templates_enabled_order 
    ON agent_templates(enabled, display_order);

-- Create index for category filtering
CREATE INDEX IF NOT EXISTS idx_agent_templates_category 
    ON agent_templates(category, enabled);
