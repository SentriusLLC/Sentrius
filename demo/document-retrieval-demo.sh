#!/bin/bash

# Document Retrieval Integration Demonstration Script
# This script demonstrates how AI agents can discover, search, and use documents

echo "==========================================="
echo "Document Retrieval Integration Demonstration"
echo "==========================================="
echo
echo "This demonstrates how AI agents can work with documents (TSGs, manuals, guides):"
echo

echo "1. AI Agent discovers available document capabilities:"
echo "   GET /api/v1/capabilities/verbs"
echo "   -> Returns list of all AI-callable verb methods including:"
echo "      - search_documents: Search for documents using text or semantic search"
echo "      - get_document: Retrieve a specific document by ID"
echo "      - get_documents_by_type: Get all documents of a specific type (TSG, MANUAL, etc.)"
echo "      - get_documents_by_tag: Get documents with specific tags"
echo "      - analyze_document: Analyze document content for metadata and suggestions"
echo

echo "2. Store a TSG document:"
echo "   POST /api/v1/documents"
echo "   Request Body:"
echo '   {'
echo '     "documentName": "SSH Connection Troubleshooting Guide",'
echo '     "documentType": "TSG",'
echo '     "content": "Step 1: Verify SSH service is running...",'
echo '     "summary": "Guide for troubleshooting SSH connection issues",'
echo '     "tags": ["ssh", "troubleshooting", "networking"],'
echo '     "classification": "UNCLASSIFIED"'
echo '   }'
echo "   -> Document is stored with auto-generated embedding for semantic search"
echo

echo "3. AI Agent searches for relevant documents:"
echo "   CALL search_documents('SSH connection problems')"
echo "   -> Returns ranked list of documents:"
echo '      [{'
echo '        "id": 1,'
echo '        "documentName": "SSH Connection Troubleshooting Guide",'
echo '        "documentType": "TSG",'
echo '        "summary": "Guide for troubleshooting SSH connection issues",'
echo '        "tags": ["ssh", "troubleshooting", "networking"],'
echo '        "similarityScore": 0.92'
echo '      }]'
echo

echo "4. AI Agent retrieves full document content:"
echo "   CALL get_document(1)"
echo "   -> Returns complete document with full content"
echo

echo "5. AI Agent digests document into memory:"
echo "   The agent can now:"
echo "   - Store key information in agent memory"
echo "   - Reference TSG steps in responses"
echo "   - Provide troubleshooting guidance based on documents"
echo

echo "6. AI Agent searches by document type:"
echo "   CALL get_documents_by_type('TSG')"
echo "   -> Returns all TSG documents"
echo

echo "7. AI Agent searches by tag:"
echo "   CALL get_documents_by_tag('ssh')"
echo "   -> Returns all documents tagged with 'ssh'"
echo

echo "8. AI Agent analyzes new document:"
echo "   CALL analyze_document(content)"
echo "   -> Returns:"
echo '      {'
echo '        "word_count": 523,'
echo '        "character_count": 3421,'
echo '        "suggested_tags": ["network", "troubleshooting", "configuration"]'
echo '      }'
echo

echo "==========================================="
echo "Use Cases:"
echo "==========================================="
echo "✓ Support agents can search TSGs for troubleshooting steps"
echo "✓ AI agents can digest operational manuals into memory"
echo "✓ Agents can find relevant guides based on user questions"
echo "✓ Automatic tagging and categorization of documents"
echo "✓ Semantic search finds conceptually similar documents"
echo "✓ Agents can reference authoritative sources in responses"
echo

echo "==========================================="
echo "Implementation Benefits:"
echo "==========================================="
echo "✓ Hybrid search (text + semantic) for better results"
echo "✓ Vector embeddings enable semantic understanding"
echo "✓ Deduplication prevents storing duplicate content"
echo "✓ Automatic embedding generation for all documents"
echo "✓ Classification and markings for access control"
echo "✓ Full CRUD operations via REST API"
echo "✓ AI-callable verbs for agent integration"
echo "✓ Compatible with existing ABAC security model"
echo

echo "==========================================="
echo "Key Components Implemented:"
echo "==========================================="
echo "✓ Document entity with vector embedding support"
echo "✓ DocumentRepository with semantic search queries"
echo "✓ DocumentService with hybrid search (text + vector)"
echo "✓ DocumentController with full REST API"
echo "✓ DocumentVerbs for AI agent integration"
echo "✓ Database migration with pgvector support"
echo "✓ Automatic embedding generation"
echo "✓ Content deduplication via checksums"
echo "✓ Document analysis for metadata extraction"
echo

echo "==========================================="
echo "Example Workflow:"
echo "==========================================="
echo "1. Administrator uploads TSG documents via API"
echo "2. System generates embeddings automatically"
echo "3. User asks agent: 'How do I fix SSH connection timeout?'"
echo "4. Agent searches documents: search_documents('SSH connection timeout')"
echo "5. Agent finds relevant TSG with 0.89 similarity score"
echo "6. Agent retrieves full TSG: get_document(tsg_id)"
echo "7. Agent stores key steps in agent memory"
echo "8. Agent provides answer with TSG reference"
echo

echo "Ready for AI agent integration!"
echo "All endpoints are secured and respect ABAC policies."
