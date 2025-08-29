-- Drop problematic old index (if it exists)
DROP INDEX IF EXISTS idx_agent_memory_embedding_classification;

-- Vector similarity search index
CREATE INDEX IF NOT EXISTS idx_agent_memory_embedding
    ON agent_memory
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Scalar filters
CREATE INDEX IF NOT EXISTS idx_agent_memory_classification
    ON agent_memory (classification);

CREATE INDEX IF NOT EXISTS idx_agent_memory_markings
    ON agent_memory (markings);

CREATE INDEX IF NOT EXISTS idx_agent_memory_expires_at
    ON agent_memory (expires_at);

-- JSONB metadata indexing
CREATE INDEX IF NOT EXISTS idx_agent_memory_metadata_gin
    ON agent_memory
    USING gin (metadata jsonb_path_ops);

-- Optional full-text index for memory_value
CREATE INDEX IF NOT EXISTS idx_agent_memory_memory_value_fts
    ON agent_memory
    USING gin (to_tsvector('english', memory_value));
