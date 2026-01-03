package io.sentrius.sso.core.services.documents;

import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.accumulo.access.AccessEvaluator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing document access control based on user attributes and markings.
 * Uses ABAC (Attribute-Based Access Control) similar to the memory access control system.
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DocumentAccessControlService {

    private final UserAttributeRepository userAttributeRepository;

    public DocumentAccessControlService(UserAttributeRepository userAttributeRepository) {
        this.userAttributeRepository = userAttributeRepository;
    }

    /**
     * Check if a user can access a specific document based on markings and attributes.
     * 
     * @param document The document to check access for
     * @param evaluator AccessEvaluator built from user's attributes (can be null)
     * @param userId The user ID attempting to access the document
     * @return true if user can access the document, false otherwise
     */
    public boolean canAccessDocument(Document document, AccessEvaluator evaluator, String userId) {
        log.debug("Evaluating document access for user: {}, document: {}", userId, document.getDocumentName());

        // If document has no markings, allow access (unclassified/public)
        if (document.getMarkings() == null || document.getMarkings().trim().isEmpty()) {
            if ("UNCLASSIFIED".equalsIgnoreCase(document.getClassification()) || 
                "PUBLIC".equalsIgnoreCase(document.getClassification())) {
                log.debug("Document is unclassified/public with no markings - access granted");
                return true;
            }
        }

        // If user is the creator, allow access
        if (userId != null && userId.equals(document.getCreatedBy())) {
            log.debug("User is the document creator - access granted");
            return true;
        }

        // Check USER: markings (private user-specific documents)
        if (document.getMarkings() != null && document.getMarkings().contains("USER:")) {
            String[] markingsArray = document.getMarkings().split(",");
            for (String marking : markingsArray) {
                if (marking.trim().startsWith("USER:")) {
                    String markedUserId = marking.trim().substring(5);
                    if (userId != null && userId.equals(markedUserId)) {
                        log.debug("USER marking matched - access granted to owning user: {}", userId);
                        return true;
                    }
                }
            }
            log.debug("USER marking(s) present but user {} does not match - access denied", userId);
            return false;
        }

        // Check if document is PUBLIC or UNCLASSIFIED - these should be accessible to all
        if ("UNCLASSIFIED".equalsIgnoreCase(document.getClassification()) || 
            "PUBLIC".equalsIgnoreCase(document.getClassification())) {
            log.debug("Document is PUBLIC/UNCLASSIFIED - access granted");
            return true;
        }

        // Use AccessEvaluator to check if user has required markings
        if (evaluator != null && document.getMarkings() != null && !document.getMarkings().trim().isEmpty()) {
            boolean canAccess = evaluator.canAccess(document.getMarkings());
            log.debug("AccessEvaluator result for markings '{}': {}", document.getMarkings(), canAccess);
            return canAccess;
        }

        // If no evaluator and document has markings, deny access
        if (document.getMarkings() != null && !document.getMarkings().trim().isEmpty()) {
            log.debug("Document has markings but user has no authorizations - access denied");
            return false;
        }

        // Default: deny access for classified documents
        log.debug("Document is classified but access could not be determined - access denied");
        return false;
    }

    /**
     * Filter a list of documents based on user access.
     * 
     * @param documents List of documents to filter
     * @param evaluator AccessEvaluator built from user's attributes (can be null)
     * @param userId The user ID attempting to access the documents
     * @return Filtered list of documents the user can access
     */
    public List<Document> filterAccessibleDocuments(List<Document> documents, AccessEvaluator evaluator, String userId) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        return documents.stream()
                .filter(doc -> canAccessDocument(doc, evaluator, userId))
                .collect(Collectors.toList());
    }

    /**
     * Get user attributes as a map for logging/debugging
     */
    public Map<String, Object> getUserAttributesMap(String userId) {
        if (userId == null) {
            return new HashMap<>();
        }

        List<UserAttribute> attributes = userAttributeRepository.findByUserIdAndIsActiveTrue(userId);
        Map<String, Object> attributeMap = new HashMap<>();
        
        for (UserAttribute attr : attributes) {
            attributeMap.put(attr.getAttributeName(), attr.getAttributeValue());
        }
        
        attributeMap.put("user_id", userId);
        
        log.debug("Loaded {} attributes for user: {}", attributeMap.size(), userId);
        return attributeMap;
    }

    /**
     * Check if user has specific attribute value
     */
    public boolean userHasAttributeValue(String userId, String attributeName, String attributeValue) {
        return userAttributeRepository.userHasAttributeValue(userId, attributeName, attributeValue);
    }

    /**
     * Get all active attributes for a user (for building AccessEvaluator)
     */
    public List<UserAttribute> getUserAttributes(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return userAttributeRepository.findByUserIdAndIsActiveTrue(userId);
    }
}
