-- V__drop_bad_embedding_indexes.sql

-- Drop problematic composite B-Tree indexes
DROP INDEX IF EXISTS idx_agent_memory_embedding_classification;
DROP INDEX IF EXISTS idx_agent_memory_embedding_markings;

-- Create vector similarity index
CREATE INDEX IF NOT EXISTS idx_agent_memory_embedding_ivfflat
    ON agent_memory USING ivfflat (embedding vector_l2_ops)
    WITH (lists = 100);

-- Index classification and markings separately for filtering
CREATE INDEX IF NOT EXISTS idx_agent_memory_classification
    ON agent_memory (classification);

CREATE INDEX IF NOT EXISTS idx_agent_memory_markings
    ON agent_memory (markings);
