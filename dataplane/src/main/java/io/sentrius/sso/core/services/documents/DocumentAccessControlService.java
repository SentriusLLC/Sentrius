package io.sentrius.sso.core.services.documents;

import io.sentrius.sso.core.model.documents.Document;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.accumulo.access.AccessEvaluator;
import org.apache.accumulo.access.Authorizations;
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
     * Access control is driven by markings:
     * - No markings (null/empty): PUBLIC - accessible to all authenticated users
     * - USER:username marking: Private to that specific user
     * - TEAM:teamname marking: Accessible to team members
     * - Other markings: Evaluated via AccessEvaluator against user's authorizations
     *
     * @param document The document to check access for
     * @param evaluator AccessEvaluator built from user's attributes (can be null)
     * @param userId The user ID attempting to access the document
     * @return true if user can access the document, false otherwise
     */
    public boolean canAccessDocument(Document document, AccessEvaluator evaluator, String userId) {
        log.debug("Evaluating document access for user: {}, document: {}", userId, document.getDocumentName());

        // If document has no markings, it's PUBLIC - allow access
        if (document.isPublic()) {
            log.debug("Document has no markings (PUBLIC) - access granted");
            return true;
        }

        // If user is the creator, allow access
        if (userId != null && userId.equals(document.getCreatedBy())) {
            log.debug("User is the document creator - access granted");
            return true;
        }

        // Check USER: markings (private user-specific documents)
        if (document.isUserPrivate()) {
            List<String> privateUserIds = document.getPrivateUserId();
            if (userId != null && privateUserIds.contains(userId)) {
                log.debug("USER marking matched - access granted to owning user: {}", userId);
                return true;
            }
            log.debug("USER marking present but user {} does not match - access denied", userId);
            return false;
        }

        // Use AccessEvaluator to check if user has required markings
        if (evaluator != null && document.requiresMarkingsAccess()) {
            boolean canAccess = evaluator.canAccess(document.getMarkings());
            log.debug("AccessEvaluator result for markings '{}': {}", document.getMarkings(), canAccess);
            return canAccess;
        }

        // If no evaluator but document has markings, deny access
        if (document.requiresMarkingsAccess()) {
            log.debug("Document has markings but user has no authorizations - access denied");
            return false;
        }

        // Default: deny access
        log.debug("Access could not be determined - access denied");
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

    /**
     * Check if a user can access a knowledge graph node based on markings and attributes.
     * Uses the same markings-driven ABAC logic as document access control.
     *
     * @param node The knowledge graph node to check access for
     * @param userId The user ID attempting to access the node
     * @return true if user can access the node, false otherwise
     */
    public boolean canAccessNode(io.sentrius.sso.core.model.documents.KnowledgeGraphNode node, String userId) {
        log.debug("Evaluating knowledge graph node access for user: {}, node: {}", userId, node.getName());

        // If node has no markings, it's PUBLIC - allow access
        if (node.getMarkings() == null || node.getMarkings().trim().isEmpty()) {
            log.debug("Node has no markings (PUBLIC) - access granted");
            return true;
        }

        // If user is the creator, allow access
        if (userId != null && userId.equals(node.getCreatedBy())) {
            log.debug("User is the creator of the node - access granted");
            return true;
        }

        // Check for user-specific markings (e.g., "USER:username")
        if (node.getMarkings().contains("USER:")) {
            if (node.getMarkings().contains("USER:" + userId)) {
                log.debug("Node has user-specific marking - access granted");
                return true;
            }
            log.debug("Node has USER: marking but not for this user - access denied");
            return false;
        }

        // Build AccessEvaluator for ABAC check
        List<UserAttribute> userAttributes = getUserAttributes(userId);
        if (userAttributes.isEmpty()) {
            log.debug("No attributes found for user - access denied");
            return false;
        }

        List<Authorizations> authorizationsList = new ArrayList<>();
        for (UserAttribute attr : userAttributes) {
            authorizationsList.add(Authorizations.of(attr.getAttributeValue()));
        }
        AccessEvaluator evaluator = AccessEvaluator.of(authorizationsList);

        // Evaluate access using ABAC
        try {
            boolean canAccess = evaluator.canAccess(node.getMarkings().getBytes());
            log.debug("ABAC evaluation result for node: {}", canAccess);
            return canAccess;
        } catch (Exception e) {
            log.error("Error evaluating ABAC access for node: {}", e.getMessage());
            return false;
        }
    }
}
