CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_command_pattern_trgm ON command_categories USING gin (pattern gin_trgm_ops);
