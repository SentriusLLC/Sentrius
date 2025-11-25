-- Add pod_name column to configuration_options for pod-specific overrides
-- NULL pod_name represents global configuration options that apply to all pods
-- Pod-specific overrides take precedence over global overrides when queried
ALTER TABLE configuration_options ADD COLUMN IF NOT EXISTS pod_name VARCHAR(255);

-- Create composite index for efficient pod-based queries (pod_name, configuration_name)
-- Queries filtering by pod_name will use this index
-- Note: If you need to query by configuration_name alone frequently, consider adding a separate index
CREATE INDEX IF NOT EXISTS idx_config_pod_name ON configuration_options (pod_name, configuration_name);
