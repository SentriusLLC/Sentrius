-- Migration to add documents table for document retrieval and analysis
-- Version: 40
-- Description: Create documents table with vector search support

CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    document_name VARCHAR(500) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    content_type VARCHAR(100) DEFAULT 'text/plain',
    summary TEXT,
    tags TEXT,
    classification VARCHAR(50) DEFAULT 'UNCLASSIFIED',
    markings VARCHAR(500),
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 1,
    metadata JSONB,
    embedding vector(1536),
    file_path VARCHAR(1000),
    file_size BIGINT,
    checksum VARCHAR(64)
);

-- Create indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_document_type ON documents(document_type);
CREATE INDEX IF NOT EXISTS idx_document_name ON documents(document_name);
CREATE INDEX IF NOT EXISTS idx_created_by ON documents(created_by);
CREATE INDEX IF NOT EXISTS idx_classification ON documents(classification);
CREATE INDEX IF NOT EXISTS idx_checksum ON documents(checksum);

-- Create vector index for similarity search (using IVFFlat for efficient similarity search)
-- This requires pgvector extension to be installed
CREATE INDEX IF NOT EXISTS idx_documents_embedding ON documents USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Add comment to table
COMMENT ON TABLE documents IS 'Stores documents (TSGs, manuals, guides) for retrieval and analysis by AI agents';
COMMENT ON COLUMN documents.embedding IS 'Vector embedding for semantic search (1536 dimensions for OpenAI embeddings)';
COMMENT ON COLUMN documents.checksum IS 'SHA-256 checksum for content deduplication';
