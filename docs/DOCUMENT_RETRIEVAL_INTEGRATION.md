# Document Retrieval Integration

This document describes the document retrieval and analysis integration added to Sentrius, enabling AI agents to work with documents (TSGs, manuals, guides, policies) through a comprehensive API and verb system. **Supports both local storage and external document retrieval from HTTP(S), with pluggable architecture for additional sources.**

## Overview

The document retrieval system provides:
- Storage and management of documents with automatic semantic indexing
- **External document retrieval from HTTP(S) URLs**
- **Pluggable architecture for additional sources (S3, SharePoint, etc.)**
- Hybrid search combining text and vector similarity
- AI agent integration through callable verbs
- Full CRUD operations via REST API
- Support for multiple document types and classifications
- Content deduplication and metadata extraction

## Architecture

### Components

1. **Document Entity** (`dataplane/src/main/java/io/sentrius/sso/core/model/documents/Document.java`)
   - JPA entity for storing documents
   - Vector embedding support (1536 dimensions for OpenAI embeddings)
   - Automatic timestamp and version management
   - Cosine similarity calculation built-in

2. **DocumentDTO** (`core/src/main/java/io/sentrius/sso/core/dto/documents/DocumentDTO.java`)
   - Data transfer object for API requests/responses
   - Includes similarity scores for search results

3. **DocumentRepository** (`dataplane/src/main/java/io/sentrius/sso/core/repository/documents/DocumentRepository.java`)
   - Spring Data JPA repository
   - Native queries for vector similarity search using pgvector
   - Efficient filtering by type, tags, classification

4. **DocumentService** (`dataplane/src/main/java/io/sentrius/sso/core/services/documents/DocumentService.java`)
   - Business logic for document management
   - Hybrid search combining text and semantic search
   - Automatic embedding generation
   - Content deduplication via checksums

5. **DocumentController** (`api/src/main/java/io/sentrius/sso/controllers/api/documents/DocumentController.java`)
   - REST API endpoints
   - Secured with existing authentication
   - ABAC policy enforcement

6. **DocumentVerbs** (`enterprise-agent/src/main/java/io/sentrius/agent/analysis/agents/verbs/DocumentVerbs.java`)
   - AI-callable verbs for agent integration
   - Zero Trust authentication
   - Discoverable via capabilities endpoint

7. **External Document Retrieval** (NEW)
   - **DocumentRetrievalService Interface** - Pluggable interface for external sources
   - **HttpDocumentRetrievalService** - HTTP(S) implementation  
   - **DocumentRetrievalManager** - Manages multiple retrieval sources
   - Support for authentication headers (Bearer, API Key, custom headers)
   - Automatic metadata extraction (content-type, filename, size)

## External Document Retrieval

The system supports retrieving documents from external sources through a pluggable architecture.

### Supported Sources

Currently implemented:
- **HTTP/HTTPS**: Retrieve documents from web servers
- **Future**: S3, SharePoint, Google Drive, etc. (pluggable architecture)

### Architecture

1. **DocumentRetrievalService Interface**: Defines the contract for retrieval implementations
2. **HttpDocumentRetrievalService**: Implementation for HTTP(S) retrieval
3. **DocumentRetrievalManager**: Coordinates multiple retrieval services

### Usage Examples

#### Retrieve from HTTP URL (without storing)

## Database Schema

```sql
CREATE TABLE documents (
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
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version INTEGER DEFAULT 1,
    metadata JSONB,
    embedding vector(1536),
    file_path VARCHAR(1000),
    file_size BIGINT,
    checksum VARCHAR(64)
);

-- Indexes for efficient querying
CREATE INDEX idx_document_type ON documents(document_type);
CREATE INDEX idx_document_name ON documents(document_name);
CREATE INDEX idx_created_by ON documents(created_by);
CREATE INDEX idx_classification ON documents(classification);
CREATE INDEX idx_checksum ON documents(checksum);

-- Vector similarity index
CREATE INDEX idx_documents_embedding ON documents 
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

## REST API Endpoints

### Create Document
```http
POST /api/v1/documents
Authorization: Bearer <token>
Content-Type: application/json

{
  "documentName": "SSH Connection Troubleshooting Guide",
  "documentType": "TSG",
  "content": "Step 1: Verify SSH service is running...",
  "contentType": "text/markdown",
  "summary": "Guide for troubleshooting SSH connection issues",
  "tags": ["ssh", "troubleshooting", "networking"],
  "classification": "UNCLASSIFIED",
  "markings": "PUBLIC"
}
```

### Search Documents
```http
POST /api/v1/documents/search
Authorization: Bearer <token>
Content-Type: application/json

{
  "query": "SSH connection timeout",
  "documentType": "TSG",
  "tags": ["networking"],
  "limit": 10,
  "threshold": 0.7,
  "useSemanticSearch": true
}
```

### Get Document by ID
```http
GET /api/v1/documents/{id}
Authorization: Bearer <token>
```

### Get Documents by Type
```http
GET /api/v1/documents/type/{documentType}
Authorization: Bearer <token>
```

### Get Documents by Tag
```http
GET /api/v1/documents/tag/{tag}
Authorization: Bearer <token>
```

### Update Document
```http
PUT /api/v1/documents/{id}
Authorization: Bearer <token>
Content-Type: application/json

{
  "content": "Updated content...",
  "summary": "Updated summary",
  "tags": ["updated", "tags"]
}
```

### Delete Document
```http
DELETE /api/v1/documents/{id}
Authorization: Bearer <token>
```

### Analyze Document
```http
POST /api/v1/documents/analyze
Authorization: Bearer <token>
Content-Type: application/json

{
  "content": "Document content to analyze..."
}
```

### Get Statistics
```http
GET /api/v1/documents/statistics
Authorization: Bearer <token>
```

### Generate Missing Embeddings
```http
POST /api/v1/documents/embeddings/generate?batchSize=100
Authorization: Bearer <token>
```

### Retrieve from External Source (NEW)
```http
POST /api/v1/documents/retrieve/external
Authorization: Bearer <token>
Content-Type: application/json

{
  "sourceUrl": "https://example.com/tsg/ssh-troubleshooting.md",
  "storeDocument": true,
  "documentName": "SSH Troubleshooting Guide",
  "documentType": "TSG",
  "classification": "UNCLASSIFIED",
  "markings": "PUBLIC",
  "options": {
    "Authorization": "Bearer <external-token>",
    "Header-Custom-Auth": "value"
  }
}
```

**Response:**
```json
{
  "id": 123,
  "documentName": "SSH Troubleshooting Guide",
  "documentType": "TSG",
  "content": "# SSH Troubleshooting...",
  "contentType": "text/markdown",
  "sourceUrl": "https://example.com/tsg/ssh-troubleshooting.md",
  "hasEmbedding": true
}
```

### Get Supported External Sources (NEW)
```http
GET /api/v1/documents/external/sources
Authorization: Bearer <token>
```

**Response:**
```json
{
  "supported_sources": ["http", "https"],
  "count": 2
}
```

## AI Agent Verbs

### search_documents
Search for documents using text or semantic search.

**Parameters:**
- `query` (required): Search query text
- `documentType` (optional): Filter by document type (TSG, MANUAL, GUIDE, etc.)
- `tags` (optional): Array of tags to filter by
- `limit` (optional): Maximum number of results (default: 20)

**Returns:** List of DocumentDTO objects

**Example:**
```java
List<DocumentDTO> results = agentVerbs.searchDocuments(token, context);
```

### get_document
Retrieve a specific document by ID.

**Parameters:**
- `documentId` (required): The ID of the document

**Returns:** DocumentDTO object

### get_documents_by_type
Get all documents of a specific type.

**Parameters:**
- `documentType` (required): Document type (TSG, MANUAL, GUIDE, POLICY, etc.)

**Returns:** List of DocumentDTO objects

### get_documents_by_tag
Get all documents with a specific tag.

**Parameters:**
- `tag` (required): Tag to search for

**Returns:** List of DocumentDTO objects

### analyze_document
Analyze document content to extract metadata.

**Parameters:**
- `content` (required): Document content to analyze

**Returns:** Map with word_count, character_count, suggested_tags

### retrieve_external_document (NEW)
Retrieve a document from an external HTTP(S) source.

**Parameters:**
- `sourceUrl` (required): URL of the document to retrieve
- `storeDocument` (optional): Whether to store locally (default: false)
- `documentName` (optional): Name for stored document
- `documentType` (optional): Type (TSG, MANUAL, etc.)
- `classification` (optional): Security classification
- `markings` (optional): Security markings
- `Authorization` (optional): Authorization header value
- `Bearer` (optional): Bearer token for Authorization header
- `ApiKey` (optional): API key for X-API-Key header

**Returns:** DocumentDTO object with retrieved content

**Example:**
```java
// Retrieve and store a TSG from external URL
context.setArgument("sourceUrl", "https://docs.example.com/ssh-tsg.md");
context.setArgument("storeDocument", true);
context.setArgument("documentType", "TSG");
context.setArgument("Bearer", "<external-api-token>");

DocumentDTO doc = documentVerbs.retrieveExternalDocument(token, context);
```

### get_external_document_sources (NEW)
Get list of supported external document sources.

**Parameters:** None

**Returns:** List of supported source types (e.g., ["http", "https"])

## Document Types

Supported document types:
- **TSG**: Troubleshooting Guide
- **MANUAL**: User Manual or Operations Manual
- **GUIDE**: How-to Guide or Tutorial
- **POLICY**: Policy Document
- **PROCEDURE**: Standard Operating Procedure
- **FAQ**: Frequently Asked Questions
- **REFERENCE**: Reference Documentation

## Search Strategies

### Text Search
- Searches document name, content, and summary fields
- Case-insensitive pattern matching
- Good for exact phrase matching

### Semantic Search
- Uses vector embeddings for conceptual similarity
- Finds documents with similar meaning, not just keywords
- Configurable similarity threshold (0.0 to 1.0)

### Hybrid Search
- Combines text and semantic search
- Boosts exact text matches (score: 1.5x)
- Adds semantic matches above threshold
- Sorts by combined score
- Provides best of both approaches

## Integration Examples

### Agent Searching for Troubleshooting Steps

```java
// Agent context includes user's question
String userQuestion = "Why can't I connect to SSH?";

// Search for relevant TSGs
DocumentSearchDTO search = DocumentSearchDTO.builder()
    .query(userQuestion)
    .documentType("TSG")
    .limit(5)
    .threshold(0.75)
    .build();

List<DocumentDTO> tsgs = documentVerbs.searchDocuments(token, context);

// Agent can now digest TSG content
for (DocumentDTO tsg : tsgs) {
    // Store key steps in agent memory
    agentMemoryStore.storeMemory(
        agentId,
        "tsg_" + tsg.getId(),
        tsg.getContent(),
        "REFERENCE",
        new String[]{"TSG", "TROUBLESHOOTING"},
        userId
    );
}

// Agent responds with TSG-backed answer
```

### Uploading Documents via API

```bash
#!/bin/bash

# Upload a TSG document
curl -X POST https://sentrius.example.com/api/v1/documents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "documentName": "Network Connectivity TSG",
    "documentType": "TSG",
    "content": "# Network Connectivity Troubleshooting\n\n1. Check physical connections...",
    "contentType": "text/markdown",
    "summary": "Troubleshooting guide for network connectivity issues",
    "tags": ["networking", "connectivity", "troubleshooting"],
    "classification": "UNCLASSIFIED"
  }'
```

### Semantic Search Example

```bash
# Search for documents about database performance
curl -X POST https://sentrius.example.com/api/v1/documents/search \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "slow database queries",
    "useSemanticSearch": true,
    "threshold": 0.7,
    "limit": 10
  }'
```

## Testing

Comprehensive test coverage includes:

1. **DocumentServiceTest** (11 test cases)
   - Document storage and retrieval
   - Duplicate detection
   - Search functionality
   - Update and delete operations
   - Embedding generation
   - Statistics

2. **DocumentControllerTest** (8 test cases)
   - REST endpoint validation
   - Response status codes
   - Error handling
   - Security integration

3. **DocumentVerbsTest** (8 test cases)
   - Verb functionality
   - Parameter validation
   - Zero Trust integration
   - Error handling

Run tests:
```bash
# Run all document tests
mvn test -Dtest=*Document*Test

# Run specific test
mvn test -Dtest=DocumentServiceTest
```

## Security

- All endpoints require authentication
- ABAC policies enforced via classification and markings
- Content checksums prevent duplicate storage
- Audit trail via created_by and timestamps
- Zero Trust token validation for agent verbs

## Performance Considerations

- Vector embeddings cached in database
- IVFFlat index for efficient similarity search
- Batch embedding generation supported
- Configurable search limits
- Text search as fallback when embeddings unavailable

## Configuration

Required services:
- PostgreSQL with pgvector extension
- OpenAI API or compatible embedding service (optional but recommended)
- Existing Sentrius authentication infrastructure

## Demo

Run the demonstration script:
```bash
./demo/document-retrieval-demo.sh
```

This shows the complete workflow from document upload through agent discovery and search.

## Future Enhancements

Potential improvements:
- PDF/DOCX file upload support
- Multi-language document support
- Document versioning with diffs
- Collaborative editing
- Advanced LLM-based summarization
- Document chunking for large files
- OCR integration for scanned documents

## Troubleshooting

### Embeddings Not Generated

Check that:
1. EmbeddingService is available and configured
2. OpenAI API key or embedding service is accessible
3. Run manual embedding generation: `POST /api/v1/documents/embeddings/generate`

### Search Returns No Results

- Lower similarity threshold (try 0.5 instead of 0.7)
- Use text-only search: `useSemanticSearch: false`
- Check document classification/markings match user's access
- Verify documents have embeddings: `GET /api/v1/documents/statistics`

### Database Migration Failed

Ensure pgvector extension is installed:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

## References

- [pgvector Documentation](https://github.com/pgvector/pgvector)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [OpenAI Embeddings API](https://platform.openai.com/docs/guides/embeddings)
- [Sentrius ABAC Implementation](docs/ABAC_IMPLEMENTATION_GUIDE.md)
