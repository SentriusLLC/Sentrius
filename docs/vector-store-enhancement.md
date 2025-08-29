# Agent Memory Store Vector Search Enhancement

## Overview

The Agent Memory Store has been enhanced with vector search capabilities, enabling semantic similarity search while maintaining the existing ABAC (Attribute-Based Access Control) security model. This enhancement allows agents to discover conceptually related memories through embeddings rather than just exact keyword matches.

## Features

### Core Vector Store Capabilities

1. **PostgreSQL + pgvector Integration**
   - Uses pgvector extension for efficient vector operations
   - Stores 1536-dimensional embeddings (compatible with OpenAI's text-embedding-3-small)
   - Cosine similarity distance calculations

2. **Automatic Embedding Generation**
   - Integrates with OpenAI's embedding API
   - Configurable via `spring.ai.openai.api-key` property
   - Automatic embedding generation for new memories when enabled

3. **Hybrid Search Capabilities**
   - Combines vector similarity with traditional text search
   - Maintains all existing markings and classification filters
   - Preserves ABAC security model

4. **Access Control Integration**
   - All vector searches respect existing security policies
   - Markings-based filtering applied to vector results
   - User attribute validation maintained

## Database Schema Changes

### Migration V22: Vector Support

```sql
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Add embedding column to agent_memory table
ALTER TABLE agent_memory ADD COLUMN embedding vector(1536);

-- Create vector similarity index
CREATE INDEX idx_agent_memory_embedding ON agent_memory 
USING ivfflat (embedding vector_cosine_ops);

-- Create hybrid search indexes
CREATE INDEX idx_agent_memory_embedding_classification 
ON agent_memory (classification, embedding);

CREATE INDEX idx_agent_memory_embedding_markings 
ON agent_memory (markings, embedding);
```

## Configuration

### Required Properties

```properties
# OpenAI API Key (required for embedding generation)
spring.ai.openai.api-key=your-openai-api-key

# Vector store configuration (optional)
sentrius.memory.vector.dimension=1536
sentrius.memory.vector.similarity-threshold=0.7
sentrius.memory.vector.enabled=true
```

### Optional Configuration

```properties
# Database connection must support pgvector
spring.datasource.url=jdbc:postgresql://localhost:5432/sentrius_db
```

## API Endpoints

### Enhanced Memory Storage

```http
POST /api/v1/agents/memory/{agentId}?generateEmbedding=true
Content-Type: application/json

{
  "memoryKey": "user_preferences",
  "memoryValue": "User prefers dark mode and compact layouts",
  "classification": "PRIVATE",
  "markings": ["UI", "PREFERENCES"]
}
```

### Semantic Search

```http
POST /api/v1/agents/memory/search/semantic
Content-Type: application/json

{
  "query": "user interface settings",
  "limit": 10,
  "threshold": 0.7
}
```

### Agent-Specific Semantic Search

```http
POST /api/v1/agents/memory/search/semantic/{agentId}
Content-Type: application/json

{
  "query": "machine learning algorithms",
  "limit": 5,
  "threshold": 0.8
}
```

### Hybrid Search

```http
POST /api/v1/agents/memory/search/hybrid
Content-Type: application/json

{
  "searchTerm": "neural networks",
  "markings": "AI,RESEARCH",
  "limit": 10,
  "threshold": 0.7
}
```

### Embedding Management

```http
# Generate embeddings for existing memories without embeddings
POST /api/v1/agents/memory/embeddings/generate?batchSize=100

# Get vector store statistics
GET /api/v1/agents/memory/statistics/vector
```

## Usage Examples

### Java Service Layer

```java
@Autowired
private VectorAgentMemoryStore vectorMemoryStore;

// Store memory with automatic embedding generation
AgentMemory memory = vectorMemoryStore.storeMemoryWithEmbedding(
    "agent-001",
    "conversation_summary", 
    "Discussion about machine learning best practices",
    "SHARED",
    new String[]{"AI", "CONVERSATION"},
    "user-123"
);

// Find semantically similar memories
List<AgentMemory> similar = vectorMemoryStore.findSimilarMemories(
    "artificial intelligence techniques", 
    "user-123", 
    10, 
    0.7
);

// Hybrid search with markings filter
List<AgentMemory> results = vectorMemoryStore.hybridSearch(
    "deep learning", 
    "AI,RESEARCH", 
    "user-123", 
    5, 
    0.8
);
```

### REST API Usage

```bash
# Store memory with embedding
curl -X POST "http://localhost:8080/api/v1/agents/memory/agent-001?generateEmbedding=true" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "memoryKey": "ai_research_notes",
    "memoryValue": "Recent advances in transformer architectures show promising results",
    "classification": "SHARED",
    "markings": ["AI", "RESEARCH", "TRANSFORMERS"]
  }'

# Semantic search
curl -X POST "http://localhost:8080/api/v1/agents/memory/search/semantic" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "query": "neural network architectures",
    "limit": 5,
    "threshold": 0.75
  }'

# Check vector store statistics
curl -X GET "http://localhost:8080/api/v1/agents/memory/statistics/vector" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

## Search Patterns

### 1. Exact Match + Semantic Fallback

```java
// First try exact match, then semantic if no results
List<AgentMemory> exact = persistentMemoryStore.findMemoriesByMarkings("AI", userId);
if (exact.isEmpty()) {
    List<AgentMemory> semantic = vectorMemoryStore.findSimilarMemories("AI", userId, 10, 0.7);
}
```

### 2. Hybrid Search for Best Coverage

```java
// Combine text matching with semantic similarity
List<AgentMemory> hybrid = vectorMemoryStore.hybridSearch(
    "machine learning", "AI", userId, 10, 0.7);
```

### 3. Cross-Agent Knowledge Discovery

```java
// Find related memories across all accessible agents
List<AgentMemory> discoveries = vectorMemoryStore.findSimilarMemories(
    "recommendation systems", userId, 20, 0.6);
```

## Performance Considerations

### Embedding Generation

- Embeddings are generated asynchronously when possible
- Batch processing available for existing memories
- OpenAI API rate limits apply (consider caching strategies)

### Vector Search Performance

- pgvector indexes optimize similarity queries
- Consider partitioning for large datasets
- Monitor query performance and adjust similarity thresholds

### Storage Impact

- Each embedding adds ~6KB per memory (1536 float values)
- Consider memory lifecycle policies for embedding cleanup
- Index maintenance overhead for large datasets

## Security Model

### Access Control Preservation

All vector search operations maintain existing security:

- **ABAC Policies**: User attributes checked before returning results
- **Markings**: Classification and markings filters applied to vector results  
- **Agent Ownership**: Agent-specific searches respect ownership rules
- **Expiration**: Expired memories excluded from vector searches

### Privacy Considerations

- Embeddings contain semantic information about original text
- Consider classification-based embedding access policies
- Audit trail maintained for all vector operations

## Monitoring and Maintenance

### Statistics Available

```json
{
  "total_memories": 1500,
  "memories_with_embeddings": 1200,
  "embedding_coverage_percentage": 80.0,
  "embedding_service_available": true,
  "vector_store_enabled": true
}
```

### Maintenance Operations

```bash
# Generate missing embeddings
curl -X POST "http://localhost:8080/api/v1/agents/memory/embeddings/generate?batchSize=50"

# Clean up expired memories (includes embeddings)
curl -X POST "http://localhost:8080/api/v1/agents/memory/cleanup/expired"
```

## Migration Path

### For Existing Installations

1. **Database Setup**: Apply migration V22 to add vector support
2. **Configuration**: Add OpenAI API key to configuration  
3. **Embedding Generation**: Use batch endpoint to generate embeddings for existing memories
4. **Application Update**: Deploy updated services with vector capabilities
5. **Verification**: Check vector store statistics and test semantic search

### Gradual Adoption

- Vector features are optional and backwards compatible
- Existing text search continues to work unchanged
- Embeddings generated on-demand for new memories
- Fallback to text search when vector search unavailable

## Troubleshooting

### Common Issues

1. **No embeddings generated**: Check OpenAI API key configuration
2. **Slow vector queries**: Verify pgvector indexes are created
3. **Memory without embeddings**: Use batch generation endpoint
4. **API rate limits**: Implement request throttling for embedding generation

### Logs to Monitor

```
# Embedding service availability
VectorStoreConfig: Vector store configuration: enabled=true

# Embedding generation
VectorAgentMemoryStore: Generated embedding for memory: agent=agent-001, key=summary

# Search performance  
VectorAgentMemoryStore: Semantic search query: neural networks, limit: 10, threshold: 0.7
```

## Future Enhancements

### Planned Features

- **Embedding Model Selection**: Support for different embedding models
- **Vector Quantization**: Optimize storage for large scale deployments
- **Semantic Clustering**: Group related memories automatically
- **Cross-Modal Search**: Image and text embedding integration
- **Vector Database Options**: Support for specialized vector databases

This enhancement provides a solid foundation for semantic memory search while maintaining the security and access control features of the existing system.