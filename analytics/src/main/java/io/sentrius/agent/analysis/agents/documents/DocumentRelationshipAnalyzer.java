package io.sentrius.agent.analysis.agents.documents;


import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.core.type.TypeReference;

import io.sentrius.sso.core.config.SystemOptions;

import io.sentrius.sso.core.dto.UserDTO;

import io.sentrius.sso.core.dto.agents.AgentExecution;

import io.sentrius.sso.core.dto.documents.DocumentDTO;

import io.sentrius.sso.core.exceptions.ZtatException;

import io.sentrius.sso.core.services.agents.AgentExecutionService;

import io.sentrius.sso.core.services.agents.LLMService;

import io.sentrius.sso.core.services.agents.ZeroTrustClientService;

import io.sentrius.sso.genai.Message;

import io.sentrius.sso.genai.model.LLMRequest;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Service;


import java.time.Instant;

import java.time.temporal.ChronoUnit;

import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

import java.util.stream.Collectors;


/**

 * Analyzes documents using LLM to discover semantic relationships.

 * This service runs periodically to:

 * 1. Find documents that haven't been analyzed for relationships

 * 2. Use LLM to understand document content and identify related documents

 * 3. Create semantic relationships like REFERENCES, RELATED_TO, SUPERSEDES, etc.

 *

 * Features intelligent caching to avoid reprocessing the same set of documents.

 */

@Slf4j

@Service

public class DocumentRelationshipAnalyzer {


    private final LLMService llmService;

    private final ZeroTrustClientService zeroTrustClientService;

    private final ObjectMapper objectMapper;

    private final SystemOptions systemOptions;


    private static final String SYSTEM_USER = "document-analyzer";

    private final AgentExecution agentExecution;


    // Relationship types that can be discovered by LLM analysis

    public static final String REL_REFERENCES = "REFERENCES";           // Document A references Document B

    public static final String REL_RELATED_TO = "RELATED_TO";           // Semantically related content

    public static final String REL_SUPERSEDES = "SUPERSEDES";           // Document A replaces Document B

    public static final String REL_DEPENDS_ON = "DEPENDS_ON";           // Document A depends on Document B

    public static final String REL_CONTRADICTS = "CONTRADICTS";         // Documents have conflicting information

    public static final String REL_EXTENDS = "EXTENDS";                 // Document A extends/builds on Document B

    public static final String REL_SUMMARIZES = "SUMMARIZES";           // Document A summarizes Document B


    // Cache configuration

    private static final long CACHE_EXPIRY_HOURS = 24;

    private final Map<String, CachedAnalysis> analysisCache = new ConcurrentHashMap<>();


    /**

     * Cached analysis result with expiration.

     */

    private static class CachedAnalysis {

        final String documentSetHash;

        final Instant analyzedAt;

        final int relationshipsDiscovered;


        CachedAnalysis(String documentSetHash, int relationshipsDiscovered) {

            this.documentSetHash = documentSetHash;

            this.analyzedAt = Instant.now();

            this.relationshipsDiscovered = relationshipsDiscovered;

        }


        boolean isExpired() {

            return Instant.now().isAfter(analyzedAt.plus(CACHE_EXPIRY_HOURS, ChronoUnit.HOURS));

        }


        long getAgeHours() {

            return ChronoUnit.HOURS.between(analyzedAt, Instant.now());

        }

    }


    @Autowired

    public DocumentRelationshipAnalyzer(

        LLMService llmService,

        AgentExecutionService agentExecutionService,

        ZeroTrustClientService zeroTrustClientService,

        ObjectMapper objectMapper,

        SystemOptions systemOptions) {

        this.llmService = llmService;

        this.zeroTrustClientService = zeroTrustClientService;

        this.objectMapper = objectMapper;

        this.systemOptions = systemOptions;


        UserDTO systemUser = UserDTO.builder()

            .username(SYSTEM_USER)

            .build();

        this.agentExecution = agentExecutionService.getAgentExecution(systemUser);

        this.agentExecution.setCommunicationId(UUID.randomUUID().toString());


        log.info("DocumentRelationshipAnalyzer initialized with agent execution: {}", SYSTEM_USER);

    }


    /**

     * Scheduled task to analyze documents for relationships.

     * Uses dynamic sleep interval from SystemOptions.documentRelationshipsSleep.

     * Runs periodically to process new or updated documents.

     * Features intelligent caching to avoid reprocessing unchanged document sets.

     */

    @Scheduled(fixedDelayString = "#{systemOptions.documentRelationshipsSleep}", initialDelayString = "#{systemOptions.documentRelationshipsSleep}")

    public void analyzeDocumentRelationships() {

        log.info("Starting scheduled document relationship analysis");


        try {

            agentExecution.setCommunicationId(UUID.randomUUID().toString());


            // Use search endpoint with empty query to get all documents

            Map<String, Object> searchRequest = new HashMap<>();

            searchRequest.put("query", "");

            searchRequest.put("limit", 100);

            searchRequest.put("useSemanticSearch", false);


            String response = zeroTrustClientService.callPostOnApi(

                agentExecution,

                "/api/v1/documents/search",

                searchRequest

            );


            if (response == null || response.trim().isEmpty()) {

                log.warn("Received empty response from documents search API");

                return;

            }


            if (response.trim().startsWith("<")) {

                log.error("Received HTML response instead of JSON from documents API. This usually indicates authentication failure or API error. Response preview: {}",

                    response.length() > 200 ? response.substring(0, 200) : response);

                return;

            }


            List<DocumentDTO> documents;

            try {

                documents = objectMapper.readValue(response,

                    new TypeReference<List<DocumentDTO>>() {});

            } catch (Exception parseEx) {

                log.error("Failed to parse documents API response as JSON. Response preview: {}",

                    response.length() > 500 ? response.substring(0, 500) : response, parseEx);

                return;

            }


            if (documents.isEmpty()) {

                log.debug("No documents to analyze for relationships");

                return;

            }


            // Check cache: compute hash of current document set

            String documentSetHash = computeDocumentSetHash(documents);

            CachedAnalysis cachedAnalysis = analysisCache.get("document_relationships");


            if (cachedAnalysis != null && !cachedAnalysis.isExpired() &&

                documentSetHash.equals(cachedAnalysis.documentSetHash)) {

                log.info("📦 Cache HIT: Document set unchanged (hash: {}). Skipping analysis. " +

                        "Last analyzed {} hours ago, {} relationships discovered previously.",

                    documentSetHash.substring(0, 8), cachedAnalysis.getAgeHours(),

                    cachedAnalysis.relationshipsDiscovered);

                return;

            }


            if (cachedAnalysis != null) {

                if (cachedAnalysis.isExpired()) {

                    log.info("🕐 Cache EXPIRED: Last analysis was {} hours ago. Re-analyzing documents.",

                        cachedAnalysis.getAgeHours());

                } else {

                    log.info("📝 Document set CHANGED: Hash mismatch (old: {}, new: {}). Re-analyzing.",

                        cachedAnalysis.documentSetHash.substring(0, 8), documentSetHash.substring(0, 8));

                }

            } else {

                log.info("🆕 First analysis: No cache entry found. Analyzing all documents.");

            }


            List<DocumentDTO> documentsToAnalyze = documents.stream()

                .limit(20)

                .collect(Collectors.toList());


            log.info("Found {} documents to analyze for relationships", documentsToAnalyze.size());


            int totalRelationshipsDiscovered = 0;
            int totalConceptsExtracted = 0;

            for (DocumentDTO document : documentsToAnalyze) {

                try {

                    int relationships = analyzeDocumentForRelationships(document);

                    totalRelationshipsDiscovered += relationships;
                    
                    // Extract rich content and concepts from document
                    int concepts = enrichDocumentKnowledgeGraph(document);
                    totalConceptsExtracted += concepts;

                } catch (Exception e) {

                    log.error("Failed to analyze relationships for document {}: {}",

                        document.getId(), e.getMessage());

                }

            }


            // Update cache after successful analysis

            analysisCache.put("document_relationships",

                new CachedAnalysis(documentSetHash, totalRelationshipsDiscovered));

            log.info("✅ Analysis complete. Cache updated. Total relationships: {}, Concepts extracted: {}",

                totalRelationshipsDiscovered, totalConceptsExtracted);


            log.info("Completed document relationship analysis");

        } catch (Exception | ZtatException e) {

            log.error("Error during document relationship analysis: {}", e.getMessage(), e);

        }

    }


    /**

     * Analyze a single document for relationships with other documents.

     * @return Number of relationships discovered

     */

    public int analyzeDocumentForRelationships(DocumentDTO document) {

        log.info("Analyzing relationships for document: {} ({})", document.getDocumentName(), document.getId());


        try {

            agentExecution.setCommunicationId(UUID.randomUUID().toString());


            // Use search endpoint with empty query to get all documents

            Map<String, Object> searchRequest = new HashMap<>();

            searchRequest.put("query", "");

            searchRequest.put("limit", 100);

            searchRequest.put("useSemanticSearch", false);


            String response = zeroTrustClientService.callPostOnApi(

                agentExecution,

                "/api/v1/documents/search",

                searchRequest

            );


            if (response == null || response.trim().isEmpty()) {

                log.warn("Received empty response from documents search API for relationship analysis");

                return 0;

            }


            if (response.trim().startsWith("<")) {

                log.error("Received HTML response instead of JSON from documents API. Authentication may have failed.");

                return 0;

            }


            List<DocumentDTO> allDocuments;

            try {

                allDocuments = objectMapper.readValue(response,

                    new TypeReference<List<DocumentDTO>>() {});

            } catch (Exception parseEx) {

                log.error("Failed to parse documents API response: {}", parseEx.getMessage());

                return 0;

            }


            List<DocumentDTO> otherDocuments = allDocuments.stream()

                .filter(d -> !d.getId().equals(document.getId()))

                .limit(50)

                .collect(Collectors.toList());


            if (otherDocuments.isEmpty()) {

                log.debug("No other documents to compare against");

                return 0;

            }


            String documentSummary = buildDocumentSummary(document);

            List<Map<String, String>> otherDocSummaries = otherDocuments.stream()

                .map(this::buildDocumentSummaryMap)

                .collect(Collectors.toList());


            List<DiscoveredRelationship> relationships = discoverRelationshipsWithLLM(

                document.getId(), documentSummary, otherDocSummaries);


            String sourceNodeId = "document:" + document.getId();

            int createdCount = 0;

            for (DiscoveredRelationship rel : relationships) {

                try {

                    agentExecution.setCommunicationId(UUID.randomUUID().toString());


                    Map<String, Object> relationshipRequest = new HashMap<>();

                    relationshipRequest.put("fromNodeId", sourceNodeId);

                    relationshipRequest.put("toNodeId", "document:" + rel.targetDocumentId);

                    relationshipRequest.put("relationshipType", rel.relationshipType);

                    relationshipRequest.put("weight", rel.confidence);


                    String relationshipResponse = zeroTrustClientService.callPostOnApi(

                        agentExecution,

                        "/api/v1/knowledge-graph/relationships",

                        relationshipRequest

                    );


                    if (relationshipResponse != null) {

                        createdCount++;

                        log.info("Created LLM-discovered relationship: {} -[{}]-> {} (confidence: {})",

                            sourceNodeId, rel.relationshipType, rel.targetDocumentId, rel.confidence);

                    }

                } catch (Exception e) {

                    log.warn("Failed to create relationship {} -> {}: {}",

                        sourceNodeId, rel.targetDocumentId, e.getMessage());

                }

            }


            log.info("Discovered {} relationships for document {}", relationships.size(), document.getId());

            return createdCount;


        } catch (Exception | ZtatException e) {

            log.error("Failed to analyze document relationships: {}", e.getMessage(), e);

            return 0;

        }

    }


    /**
     * Enrich the knowledge graph with extracted content from a document.
     * Uses LLM to extract key facts, procedures, concepts, and topics.
     * Stores extracted information in the document node and creates concept nodes.
     * 
     * @return Number of concepts extracted and stored
     */
    private int enrichDocumentKnowledgeGraph(DocumentDTO document) {
        log.info("Enriching knowledge graph for document: {} ({})", document.getDocumentName(), document.getId());
        
        try {
            agentExecution.setCommunicationId(UUID.randomUUID().toString());
            
            // Extract rich content using LLM
            ExtractedContent extractedContent = extractDocumentContent(document);
            
            if (extractedContent == null) {
                log.warn("Failed to extract content from document {}", document.getId());
                return 0;
            }
            
            // Update document node with extracted metadata
            updateDocumentNodeWithExtractedContent(document.getId(), extractedContent);
            
            // Create concept nodes and link them to document - pass markings for access control inheritance
            int conceptsCreated = createConceptNodes(document.getId(), extractedContent, document.getMarkings());

            log.info("Enriched document {} with {} key facts and {} concepts", 
                document.getId(), extractedContent.keyFacts.size(), conceptsCreated);
            
            return conceptsCreated;
            
        } catch (Exception e) {
            log.error("Failed to enrich knowledge graph for document {}: {}", document.getId(), e.getMessage(), e);
            return 0;
        }
    }


    /**
     * Use LLM to extract rich content from a document.
     */
    private ExtractedContent extractDocumentContent(DocumentDTO document) {
        try {
            agentExecution.setCommunicationId(UUID.randomUUID().toString());
            
            String systemPrompt = "You are a document analysis assistant that extracts structured information from documents. " +
                "Extract key facts, procedures, topics, and concepts. Always respond with valid JSON.";
            
            String userPrompt = buildContentExtractionPrompt(document);
            
            LLMRequest request = LLMRequest.builder()
                .model("gpt-4.1")
                .messages(Arrays.asList(
                    Message.builder().role("system").content(systemPrompt).build(),
                    Message.builder().role("user").content(userPrompt).build()
                ))
                .temperature(0.3F)
                .maxTokens(2000)
                .build();
            
            String response = llmService.askQuestion(agentExecution, request);
            
            return parseExtractedContent(response);
            
        } catch (Exception | ZtatException e) {
            log.error("LLM content extraction failed: {}", e.getMessage(), e);
            return null;
        }
    }


    /**
     * Build prompt for content extraction.
     */
    private String buildContentExtractionPrompt(DocumentDTO document) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Analyze the following document and extract structured information.\n\n");
        prompt.append("DOCUMENT:\n");
        prompt.append("Name: ").append(document.getDocumentName()).append("\n");
        prompt.append("Type: ").append(document.getDocumentType()).append("\n");
        
        if (document.getSummary() != null) {
            prompt.append("Summary: ").append(document.getSummary()).append("\n");
        }
        
        if (document.getContent() != null) {
            // Include full content, truncate if extremely long
            String content = document.getContent();
            if (content.length() > 8000) {
                content = content.substring(0, 8000) + "...[truncated]";
            }
            prompt.append("\nCONTENT:\n").append(content).append("\n");
        }
        
        prompt.append("\nExtract the following information in JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"keyFacts\": [\"fact1\", \"fact2\", ...],  // Important facts from the document\n");
        prompt.append("  \"procedures\": [{\"name\": \"procedure name\", \"steps\": [\"step1\", \"step2\"]}, ...],  // Step-by-step procedures\n");
        prompt.append("  \"concepts\": [\"concept1\", \"concept2\", ...],  // Key concepts, topics, technologies\n");
        prompt.append("  \"topics\": [\"topic1\", \"topic2\", ...]  // Main topics covered\n");
        prompt.append("}\n\n");
        prompt.append("Be specific and extract concrete, actionable information. Include at least 3-10 items per category if available.");
        
        return prompt.toString();
    }


    /**
     * Parse LLM response to extract structured content.
     */
    private ExtractedContent parseExtractedContent(String response) {
        try {
            // Handle different response formats from LLMService
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode dataNode = null;
            
            // Try different response structures
            if (rootNode.has("output") && rootNode.get("output").isArray()) {
                JsonNode outputArray = rootNode.get("output");
                if (!outputArray.isEmpty()) {
                    JsonNode firstOutput = outputArray.get(0);
                    if (firstOutput.has("content") && firstOutput.get("content").isArray()) {
                        JsonNode contentArray = firstOutput.get("content");
                        if (!contentArray.isEmpty()) {
                            JsonNode firstContent = contentArray.get(0);
                            if (firstContent.has("text")) {
                                String textContent = firstContent.get("text").asText();
                                dataNode = objectMapper.readTree(textContent);
                            }
                        }
                    }
                }
            } else if (rootNode.has("choices")) {
                dataNode = objectMapper.readTree(rootNode.get("choices").get(0).get("message").get("content").asText());
            } else if (rootNode.isArray() && !rootNode.isEmpty()) {
                dataNode = rootNode.get(0);
            } else {
                dataNode = rootNode;
            }
            
            if (dataNode == null) {
                log.warn("Could not parse extracted content from response");
                return null;
            }
            
            ExtractedContent content = new ExtractedContent();
            
            // Extract key facts
            if (dataNode.has("keyFacts") && dataNode.get("keyFacts").isArray()) {
                for (JsonNode fact : dataNode.get("keyFacts")) {
                    content.keyFacts.add(fact.asText());
                }
            }
            
            // Extract procedures
            if (dataNode.has("procedures") && dataNode.get("procedures").isArray()) {
                for (JsonNode proc : dataNode.get("procedures")) {
                    if (proc.has("name")) {
                        ExtractedProcedure procedure = new ExtractedProcedure();
                        procedure.name = proc.get("name").asText();
                        if (proc.has("steps") && proc.get("steps").isArray()) {
                            for (JsonNode step : proc.get("steps")) {
                                procedure.steps.add(step.asText());
                            }
                        }
                        content.procedures.add(procedure);
                    }
                }
            }
            
            // Extract concepts
            if (dataNode.has("concepts") && dataNode.get("concepts").isArray()) {
                for (JsonNode concept : dataNode.get("concepts")) {
                    content.concepts.add(concept.asText());
                }
            }
            
            // Extract topics
            if (dataNode.has("topics") && dataNode.get("topics").isArray()) {
                for (JsonNode topic : dataNode.get("topics")) {
                    content.topics.add(topic.asText());
                }
            }
            
            return content;
            
        } catch (Exception e) {
            log.error("Failed to parse extracted content: {}", e.getMessage(), e);
            return null;
        }
    }


    /**
     * Update document node with extracted content.
     */
    private void updateDocumentNodeWithExtractedContent(Long documentId, ExtractedContent content) {
        try {
            agentExecution.setCommunicationId(UUID.randomUUID().toString());
            
            // Build properties map with extracted content
            Map<String, Object> properties = new HashMap<>();
            properties.put("keyFacts", content.keyFacts);
            properties.put("procedures", content.procedures.stream()
                .map(p -> {
                    Map<String, Object> procMap = new HashMap<>();
                    procMap.put("name", p.name);
                    procMap.put("steps", p.steps);
                    return procMap;
                })
                .collect(Collectors.toList()));
            properties.put("concepts", content.concepts);
            properties.put("topics", content.topics);
            properties.put("enrichedAt", Instant.now().toString());
            
            // Update the node via PATCH API
            try {
                String updateResponse = zeroTrustClientService.callPatchOnApi(
                    agentExecution,
                    "/knowledge-graph/nodes/document:" + documentId + "/properties",
                    properties
                );

                log.info("Successfully updated document node {} with extracted content: {} facts, {} procedures, {} concepts",
                    documentId, content.keyFacts.size(), content.procedures.size(), content.concepts.size());
                log.debug("Update response: {}", updateResponse);

            } catch (ZtatException ztatEx) {
                log.warn("ZTAT exception when updating document node {}: {}", documentId, ztatEx.getMessage());
                log.info("Would have updated document node {} with extracted content: {} facts, {} procedures, {} concepts",
                    documentId, content.keyFacts.size(), content.procedures.size(), content.concepts.size());
            } catch (Exception apiException) {
                log.error("Failed to update document node via API: {}", apiException.getMessage(), apiException);
                // Log that we tried to update but continue processing
                log.info("Would have updated document node {} with extracted content: {} facts, {} procedures, {} concepts",
                    documentId, content.keyFacts.size(), content.procedures.size(), content.concepts.size());
            }

        } catch (Exception e) {
            log.error("Failed to update document node: {}", e.getMessage(), e);
        }
    }


    /**
     * Create concept nodes in knowledge graph and link them to the document.
     * Nodes inherit markings from the source document for access control.
     *
     * @param documentId The source document ID
     * @param content The extracted content containing procedures and concepts
     * @param sourceMarkings The markings from the source document (for access control inheritance)
     */
    private int createConceptNodes(Long documentId, ExtractedContent content, String sourceMarkings) {
        int created = 0;
        
        // Default to PUBLIC if no markings
        String effectiveMarkings = (sourceMarkings != null && !sourceMarkings.trim().isEmpty())
            ? sourceMarkings : "PUBLIC";

        // Create nodes for procedures
        for (ExtractedProcedure procedure : content.procedures) {
            try {
                agentExecution.setCommunicationId(UUID.randomUUID().toString());
                
                String conceptId = "procedure:" + sanitizeId(procedure.name) + ":" + documentId;
                
                Map<String, Object> conceptNode = new HashMap<>();
                conceptNode.put("id", conceptId);
                conceptNode.put("nodeType", "procedure");
                conceptNode.put("name", procedure.name);
                conceptNode.put("description", "Procedure: " + String.join(" → ", procedure.steps));
                conceptNode.put("steps", procedure.steps);
                conceptNode.put("sourceDocumentId", documentId);
                conceptNode.put("markings", effectiveMarkings); // Inherit markings from source document

                // Create procedure node in knowledge graph
                try {
                    String createNodeResponse = zeroTrustClientService.callPostOnApi(
                        agentExecution,
                        "/api/v1/knowledge-graph/nodes",
                        conceptNode
                    );
                    log.info("Created procedure node: {} with {} steps", procedure.name, procedure.steps.size());
                    log.debug("Create node response: {}", createNodeResponse);

                    // Create relationship from document to procedure
                    Map<String, Object> relationshipRequest = new HashMap<>();
                    relationshipRequest.put("fromNodeId", "document:" + documentId);
                    relationshipRequest.put("toNodeId", conceptId);
                    relationshipRequest.put("relationshipType", "CONTAINS_PROCEDURE");
                    relationshipRequest.put("weight", 1.0);

                    String relationshipResponse = zeroTrustClientService.callPostOnApi(
                        agentExecution,
                        "/api/v1/knowledge-graph/relationships",
                        relationshipRequest
                    );
                    log.debug("Created CONTAINS_PROCEDURE relationship: document:{} -> {}", documentId, conceptId);

                    created++;
                } catch (ZtatException ztatEx) {
                    log.warn("ZTAT exception when creating procedure node {}: {}", procedure.name, ztatEx.getMessage());
                }

            } catch (Exception e) {
                log.warn("Failed to create procedure node for {}: {}", procedure.name, e.getMessage());
            }
        }
        
        // Create nodes for key concepts
        for (String concept : content.concepts) {
            try {
                agentExecution.setCommunicationId(UUID.randomUUID().toString());
                
                String conceptId = "concept:" + sanitizeId(concept);
                
                Map<String, Object> conceptNode = new HashMap<>();
                conceptNode.put("id", conceptId);
                conceptNode.put("nodeType", "concept");
                conceptNode.put("name", concept);
                conceptNode.put("description", "Concept referenced in document");
                conceptNode.put("sourceDocumentId", documentId);
                conceptNode.put("markings", effectiveMarkings); // Inherit markings from source document

                // Create or update concept node in knowledge graph
                try {
                    String createNodeResponse = zeroTrustClientService.callPostOnApi(
                        agentExecution,
                        "/api/v1/knowledge-graph/nodes",
                        conceptNode
                    );
                    log.info("Created/updated concept node: {}", concept);
                    log.debug("Create node response: {}", createNodeResponse);

                    // Create relationship from document to concept
                    Map<String, Object> relationshipRequest = new HashMap<>();
                    relationshipRequest.put("fromNodeId", "document:" + documentId);
                    relationshipRequest.put("toNodeId", conceptId);
                    relationshipRequest.put("relationshipType", "DISCUSSES");
                    relationshipRequest.put("weight", 0.8);

                    String relationshipResponse = zeroTrustClientService.callPostOnApi(
                        agentExecution,
                        "/api/v1/knowledge-graph/relationships",
                        relationshipRequest
                    );
                    log.debug("Created DISCUSSES relationship: document:{} -> {}", documentId, conceptId);

                    created++;
                } catch (ZtatException ztatEx) {
                    log.warn("ZTAT exception when creating concept node {}: {}", concept, ztatEx.getMessage());
                }

            } catch (Exception e) {
                e.printStackTrace();
                log.warn("Failed to create concept node for {}: {}", concept, e.getMessage());
            }
        }
        
        return created;
    }


    /**
     * Sanitize a string to be used as part of a node ID.
     */
    private String sanitizeId(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }


    /**
     * Use LLM to discover semantic relationships between documents.

     */

    private List<DiscoveredRelationship> discoverRelationshipsWithLLM(

        Long sourceDocId,

        String sourceSummary,

        List<Map<String, String>> otherDocuments) {


        List<DiscoveredRelationship> relationships = new ArrayList<>();


        try {

            agentExecution.setCommunicationId(UUID.randomUUID().toString());


            String userPrompt = buildRelationshipDiscoveryPrompt(sourceDocId, sourceSummary, otherDocuments);

            String systemPrompt = "You are a document analysis assistant that identifies relationships between documents. Always respond with valid JSON.";


            LLMRequest request = LLMRequest.builder()

                .model("gpt-4.1")

                .messages(Arrays.asList(

                    Message.builder().role("system").content(systemPrompt).build(),

                    Message.builder().role("user").content(userPrompt).build()

                ))

                .temperature(0.3F)

                .maxTokens(2000)

                .build();


            String response = llmService.askQuestion(agentExecution, request);


            relationships = parseRelationshipResponse(response, sourceDocId);


        } catch (Exception e) {

            log.error("LLM relationship discovery failed: {}", e.getMessage(), e);

        } catch (ZtatException e) {

            throw new RuntimeException(e);

        }


        return relationships;

    }


    /**

     * Build the prompt for LLM relationship discovery.

     */

    private String buildRelationshipDiscoveryPrompt(

        Long sourceDocId,

        String sourceSummary,

        List<Map<String, String>> otherDocuments) {


        StringBuilder prompt = new StringBuilder();

        prompt.append("Analyze the following source document and identify its relationships with other documents.\n\n");

        prompt.append("SOURCE DOCUMENT (ID: ").append(sourceDocId).append("):\n");

        prompt.append(sourceSummary).append("\n\n");

        prompt.append("OTHER DOCUMENTS:\n");


        for (Map<String, String> doc : otherDocuments) {

            prompt.append("- ID: ").append(doc.get("id"));

            prompt.append(", Name: ").append(doc.get("name"));

            prompt.append(", Type: ").append(doc.get("type"));

            prompt.append(", Summary: ").append(doc.get("summary")).append("\n");

        }


        prompt.append("\nIdentify semantic relationships between the source document and other documents.\n");

        prompt.append("Possible relationship types:\n");

        prompt.append("- REFERENCES: Source document explicitly references the other document\n");

        prompt.append("- RELATED_TO: Documents cover related topics or concepts\n");

        prompt.append("- SUPERSEDES: Source document replaces or updates the other document\n");

        prompt.append("- DEPENDS_ON: Source document requires information from the other document\n");

        prompt.append("- EXTENDS: Source document builds upon or extends the other document\n");

        prompt.append("- SUMMARIZES: Source document summarizes the other document\n");

        prompt.append("\nIMPORTANT: Respond with ONLY a JSON array, nothing else. Use this exact format:\n");

        prompt.append("[\n");

        prompt.append("  {\"targetId\": 123, \"type\": \"RELATED_TO\", \"confidence\": 0.85, \"reason\": \"Both discuss deployment\"}\n");

        prompt.append("]\n\n");

        prompt.append("Rules:\n");

        prompt.append("- targetId MUST be the numeric document ID from the list above\n");

        prompt.append("- type MUST be one of the relationship types listed (all caps)\n");

        prompt.append("- confidence MUST be a number between 0.0 and 1.0\n");

        prompt.append("- Only include relationships with confidence >= 0.6\n");

        prompt.append("- If no relationships found, return an empty array: []\n");

        prompt.append("- Return ONLY the JSON array, no other text");


        return prompt.toString();

    }


    /**

     * Parse the LLM response to extract relationships.

     */

    private List<DiscoveredRelationship> parseRelationshipResponse(String response, Long sourceDocId) {

        List<DiscoveredRelationship> relationships = new ArrayList<>();


        try {

            // First, try to parse the full response as JSON to get the content

            JsonNode responseNode = objectMapper.readTree(response);

            String content = response;


            // If it's an OpenAI-style response, extract the content

            if (responseNode.has("choices")) {

                content = responseNode.get("choices").get(0).get("message").get("content").asText();

            } else if (responseNode.isArray() && responseNode.size() > 0) {

                // Handle array response from LLMService (with output field)

                JsonNode firstResponse = responseNode.get(0);

                if (firstResponse.has("output") && firstResponse.get("output").isArray()) {

                    JsonNode output = firstResponse.get("output").get(0);

                    if (output.has("content") && output.get("content").isArray()) {

                        JsonNode contentNode = output.get("content").get(0);

                        if (contentNode.has("text")) {

                            content = contentNode.get("text").asText();

                        }

                    }

                }

            } else if (responseNode.has("content") && responseNode.get("content").isArray()) {

                // Handle direct message format with content array (from your logs)

                JsonNode contentArray = responseNode.get("content");

                if (contentArray.size() > 0 && contentArray.get(0).has("text")) {

                    content = contentArray.get(0).get("text").asText();

                }

            }


            log.debug("Extracted content for relationship parsing: {}",

                content.length() > 200 ? content.substring(0, 200) + "..." : content);


            // Find JSON array in content

            int startIdx = content.indexOf('[');

            int endIdx = content.lastIndexOf(']');


            if (startIdx >= 0 && endIdx > startIdx) {

                String jsonArray = content.substring(startIdx, endIdx + 1);

                JsonNode arrayNode = objectMapper.readTree(jsonArray);


                log.debug("Parsed relationship array with {} elements", arrayNode.size());


                if (arrayNode.isArray()) {

                    for (JsonNode node : arrayNode) {

                        try {

                            // Log the actual relationship object, not the wrapper

                            log.debug("Processing relationship object: {}", node.toString());


                            // Check for required fields with multiple possible names

                            Long targetId = null;

                            if (node.has("targetId")) {

                                targetId = node.get("targetId").asLong();

                            } else if (node.has("target_id")) {

                                targetId = node.get("target_id").asLong();

                            } else if (node.has("targetDocumentId")) {

                                targetId = node.get("targetDocumentId").asLong();

                            } else if (node.has("id")) {

                                targetId = node.get("id").asLong();

                            }


                            if (targetId == null) {

                                log.warn("Skipping relationship - no target ID field found. Object: {}", node.toString());

                                continue;

                            }


                            String type = node.has("type") ? node.get("type").asText() :

                                (node.has("relationshipType") ? node.get("relationshipType").asText() : null);


                            if (type == null) {

                                log.warn("Skipping relationship - no type field found. Object: {}", node.toString());

                                continue;

                            }


                            double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.7;

                            String reason = node.has("reason") ? node.get("reason").asText() : "";


                            // Validate relationship type

                            if (isValidRelationshipType(type) && confidence >= 0.6) {

                                relationships.add(new DiscoveredRelationship(targetId, type, confidence, reason));

                                log.info("✅ Added relationship: document:{} -[{}]-> document:{} (confidence: {})",

                                    sourceDocId, type, targetId, confidence);

                            } else {

                                log.debug("Skipped relationship: invalid type '{}' or low confidence {}", type, confidence);

                            }

                        } catch (Exception e) {

                            log.warn("Failed to parse relationship from JSON: {}. Object: {}",

                                e.getMessage(), node.toString());

                        }

                    }

                }

            } else {

                log.warn("No JSON array found in LLM response. Content: {}",

                    content.length() > 500 ? content.substring(0, 500) + "..." : content);

            }

        } catch (Exception e) {

            log.error("Failed to parse LLM relationship response: {}. Response preview: {}",

                e.getMessage(),

                response.length() > 500 ? response.substring(0, 500) + "..." : response);

        }


        return relationships;

    }


    /**

     * Check if the relationship type is valid.

     */

    private boolean isValidRelationshipType(String type) {

        return type != null && (

            type.equals(REL_REFERENCES) ||

                type.equals(REL_RELATED_TO) ||

                type.equals(REL_SUPERSEDES) ||

                type.equals(REL_DEPENDS_ON) ||

                type.equals(REL_CONTRADICTS) ||

                type.equals(REL_EXTENDS) ||

                type.equals(REL_SUMMARIZES)

        );

    }


    /**

     * Build a summary of a document for LLM analysis.

     */

    private String buildDocumentSummary(DocumentDTO document) {

        StringBuilder sb = new StringBuilder();

        sb.append("Name: ").append(document.getDocumentName()).append("\n");

        sb.append("Type: ").append(document.getDocumentType()).append("\n");

        if (document.getSummary() != null) {

            sb.append("Summary: ").append(document.getSummary()).append("\n");

        }

        if (document.getTags() != null && document.getTags().length > 0) {

            sb.append("Tags: ").append(String.join(", ", document.getTags())).append("\n");

        }

        if (document.getContent() != null) {

            String contentPreview = document.getContent().substring(0, Math.min(500, document.getContent().length()));

            sb.append("Content preview: ").append(contentPreview);

            if (document.getContent().length() > 500) {

                sb.append("...");

            }

        }

        return sb.toString();

    }


    /**

     * Build a summary map of a document for comparison.

     */

    private Map<String, String> buildDocumentSummaryMap(DocumentDTO document) {

        Map<String, String> map = new HashMap<>();

        map.put("id", document.getId().toString());

        map.put("name", document.getDocumentName());

        map.put("type", document.getDocumentType() != null ? document.getDocumentType() : "unknown");

        map.put("summary", document.getSummary() != null ? document.getSummary() :

            (document.getContent() != null ?

                document.getContent().substring(0, Math.min(200, document.getContent().length())) : ""));

        return map;

    }


    /**

     * Internal class to represent a discovered relationship.

     */

    private static class DiscoveredRelationship {

        final Long targetDocumentId;

        final String relationshipType;

        final Double confidence;

        final String reason;


        DiscoveredRelationship(Long targetDocumentId, String relationshipType, Double confidence, String reason) {

            this.targetDocumentId = targetDocumentId;

            this.relationshipType = relationshipType;

            this.confidence = confidence;

            this.reason = reason;

        }

    }


    /**

     * Manually trigger relationship analysis for a specific document.

     * This can be called from the API when a document is uploaded.

     */

    public void triggerAnalysisForDocument(Long documentId) {

        log.info("Triggering relationship analysis for document: {}", documentId);


        try {

            agentExecution.setCommunicationId(UUID.randomUUID().toString());


            String response = zeroTrustClientService.callGetOnApi(agentExecution, "/api/v1/documents/" + documentId);


            if (response == null || response.trim().isEmpty()) {

                log.warn("Document not found for analysis: {}", documentId);

                return;

            }


            if (response.trim().startsWith("<")) {

                log.error("Received HTML response instead of JSON from documents API. Authentication may have failed.");

                return;

            }


            DocumentDTO document;

            try {

                document = objectMapper.readValue(response, DocumentDTO.class);

            } catch (Exception parseEx) {

                log.error("Failed to parse document API response: {}", parseEx.getMessage());

                return;

            }


            analyzeDocumentForRelationships(document);

        } catch (Exception | ZtatException e) {

            log.error("Failed to trigger analysis for document {}: {}", documentId, e.getMessage(), e);

        }

    }


    /**

     * Compute a hash of the document set to detect changes.

     * Hash is based on document IDs and their last modified timestamps.

     * If the set of documents or any document's content changes, the hash will differ.

     *

     * @param documents List of documents to hash

     * @return SHA-256 hash of the document set

     */

    private String computeDocumentSetHash(List<DocumentDTO> documents) {

        try {

            // Sort documents by ID for consistent hashing

            List<String> documentSignatures = documents.stream()

                .sorted(Comparator.comparing(DocumentDTO::getId))

                .map(doc -> doc.getId() + ":" +

                    (doc.getUpdatedAt() != null ? doc.getUpdatedAt() : doc.getCreatedAt()))

                .collect(Collectors.toList());


            String combined = String.join("|", documentSignatures);


            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));


            StringBuilder hexString = new StringBuilder();

            for (byte b : hashBytes) {

                String hex = Integer.toHexString(0xff & b);

                if (hex.length() == 1) hexString.append('0');

                hexString.append(hex);

            }


            return hexString.toString();

        } catch (java.security.NoSuchAlgorithmException e) {

            // Fallback to simple concatenation if SHA-256 is not available

            log.warn("SHA-256 not available, using simple hash fallback");

            return documents.stream()

                .sorted(Comparator.comparing(DocumentDTO::getId))

                .map(doc -> String.valueOf(doc.getId()))

                .collect(Collectors.joining(","));

        }

    }


    /**

     * Clear the analysis cache. Useful for testing or forced reanalysis.

     */

    public void clearCache() {

        analysisCache.clear();

        log.info("Analysis cache cleared");

    }


    /**

     * Get cache statistics for monitoring.

     */

    public Map<String, Object> getCacheStats() {

        Map<String, Object> stats = new HashMap<>();

        CachedAnalysis cached = analysisCache.get("document_relationships");


        if (cached != null) {

            stats.put("cached", true);

            stats.put("ageHours", cached.getAgeHours());

            stats.put("expired", cached.isExpired());

            stats.put("lastAnalyzedAt", cached.analyzedAt.toString());

            stats.put("relationshipsDiscovered", cached.relationshipsDiscovered);

            stats.put("documentSetHash", cached.documentSetHash.substring(0, 16) + "...");

        } else {

            stats.put("cached", false);

        }


        return stats;

    }


    /**
     * Internal class to represent extracted content from a document.
     */
    private static class ExtractedContent {
        List<String> keyFacts = new ArrayList<>();
        List<ExtractedProcedure> procedures = new ArrayList<>();
        List<String> concepts = new ArrayList<>();
        List<String> topics = new ArrayList<>();
    }


    /**
     * Internal class to represent an extracted procedure.
     */
    private static class ExtractedProcedure {
        String name;
        List<String> steps = new ArrayList<>();
    }

} 