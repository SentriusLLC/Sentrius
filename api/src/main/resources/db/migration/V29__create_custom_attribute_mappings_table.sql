-- Create custom_attribute_mappings table for storing custom attribute requirements per endpoint
CREATE TABLE IF NOT EXISTS custom_attribute_mappings (
    id BIGSERIAL PRIMARY KEY,
    endpoint VARCHAR(500) NOT NULL,
    attribute_name VARCHAR(255) NOT NULL,
    required_value VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster lookups by endpoint
CREATE INDEX IF NOT EXISTS idx_custom_attribute_mappings_endpoint 
    ON custom_attribute_mappings(endpoint) WHERE is_active = true;

-- Create index for faster lookups by attribute name
CREATE INDEX IF NOT EXISTS idx_custom_attribute_mappings_attribute_name 
    ON custom_attribute_mappings(attribute_name) WHERE is_active = true;

-- Create composite index for endpoint and active status
CREATE INDEX IF NOT EXISTS idx_custom_attribute_mappings_endpoint_active 
    ON custom_attribute_mappings(endpoint, is_active);
