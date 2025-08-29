-- Add vector support to agent memory store
-- Enable pgvector extension for PostgreSQL vector operations
CREATE EXTENSION IF NOT EXISTS vector;

-- Add embedding column to agent_memory table for semantic search
ALTER TABLE agent_memory ADD COLUMN embedding vector(1536);

-- Create index for vector similarity search using cosine distance
CREATE INDEX idx_agent_memory_embedding ON agent_memory USING ivfflat (embedding vector_cosine_ops);

-- Create additional indexes for hybrid search (combining vector with markings)
CREATE INDEX idx_agent_memory_embedding_classification ON agent_memory (classification, embedding);
CREATE INDEX idx_agent_memory_embedding_markings ON agent_memory (markings, embedding);

-- Add metadata for vector store configuration
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
('system.vector_store_config', 
 '{"dimension": 1536, "similarity_function": "cosine", "index_type": "ivfflat", "enabled": true}',
 'JSON',
 'system',
 'PUBLIC',
 'SYSTEM,VECTOR_STORE,CONFIG',
 'ALL_USERS',
 'system',
 '{"is_system": true, "category": "configuration", "version": "1.0"}'
);