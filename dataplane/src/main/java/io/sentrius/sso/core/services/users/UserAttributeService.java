package io.sentrius.sso.core.services.users;

import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.repository.UserAttributeRepository;
import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;

@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class UserAttributeService {

    private final UserAttributeRepository userAttributeRepository;
    private final KeycloakService keycloakService;

    public UserAttributeService(UserAttributeRepository userAttributeRepository, KeycloakService keycloakService) {
        this.userAttributeRepository = userAttributeRepository;
        this.keycloakService = keycloakService;
    }

    /**
     * Get all active attributes for a user
     */
    public List<UserAttribute> getUserAttributes(String userId) {
        log.debug("Getting attributes for user: {}", userId);
        return userAttributeRepository.findByUserIdAndIsActiveTrue(userId);
    }

    /**
     * Get all active user attributes across all users.
     * Used by the unified attribute management UI to display legacy attributes.
     */
    public List<UserAttribute> getAllActiveAttributes() {
        log.debug("Getting all active user attributes");
        return userAttributeRepository.findByIsActiveTrue();
    }

    /**
     * Get user attributes as a map
     */
    public Map<String, String> getUserAttributesAsMap(String userId) {
        List<UserAttribute> attributes = getUserAttributes(userId);
        return attributes.stream()
                .collect(Collectors.toMap(
                        UserAttribute::getAttributeName,
                        UserAttribute::getAttributeValue,
                        (existing, replacement) -> replacement));
    }

    /**
     * Get a specific user attribute
     */
    public Optional<UserAttribute> getUserAttribute(String userId, String attributeName) {
        return userAttributeRepository.findByUserIdAndAttributeNameAndIsActiveTrue(userId, attributeName);
    }

    /**
     * Get a user attribute value
     */
    public Optional<String> getUserAttributeValue(String userId, String attributeName) {
        return getUserAttribute(userId, attributeName)
                .map(UserAttribute::getAttributeValue);
    }

    /**
     * Set a user attribute
     */
    @Transactional
    public UserAttribute setUserAttribute(String userId, String attributeName, String attributeValue, 
                                          String attributeType, String source) {
        log.info("Setting attribute for user: {}, name: {}, value: {}", userId, attributeName, attributeValue);

        Optional<UserAttribute> existingOpt = userAttributeRepository
                .findByUserIdAndAttributeNameAndIsActiveTrue(userId, attributeName);

        UserAttribute attribute;
        if (existingOpt.isPresent()) {
            attribute = existingOpt.get();
            attribute.setAttributeValue(attributeValue);
            attribute.setAttributeType(attributeType != null ? attributeType : "STRING");
            attribute.setSource(source != null ? source : "SENTRIUS");
            log.debug("Updated existing attribute for user: {}, name: {}", userId, attributeName);
        } else {
            attribute = UserAttribute.builder()
                    .userId(userId)
                    .attributeName(attributeName)
                    .attributeValue(attributeValue)
                    .attributeType(attributeType != null ? attributeType : "STRING")
                    .source(source != null ? source : "SENTRIUS")
                    .isActive(true)
                    .syncedFromKeycloak(false)
                    .build();
            log.debug("Created new attribute for user: {}, name: {}", userId, attributeName);
        }

        // Validate the attribute value for its type
        if (!attribute.isValidForType()) {
            throw new IllegalArgumentException("Invalid value for attribute type: " + attributeType);
        }

        return userAttributeRepository.save(attribute);
    }

    /**
     * Set multiple user attributes at once
     */
    @Transactional
    public List<UserAttribute> setUserAttributes(String userId, Map<String, String> attributes, String source) {
        log.info("Setting {} attributes for user: {}", attributes.size(), userId);

        List<UserAttribute> savedAttributes = new ArrayList<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            UserAttribute attr = setUserAttribute(userId, entry.getKey(), entry.getValue(), "STRING", source);
            savedAttributes.add(attr);
        }

        return savedAttributes;
    }

    /**
     * Remove a user attribute
     */
    @Transactional
    public boolean removeUserAttribute(String userId, String attributeName) {
        log.info("Removing attribute for user: {}, name: {}", userId, attributeName);

        Optional<UserAttribute> attributeOpt = userAttributeRepository
                .findByUserIdAndAttributeNameAndIsActiveTrue(userId, attributeName);

        if (attributeOpt.isPresent()) {
            UserAttribute attribute = attributeOpt.get();
            attribute.setIsActive(false);
            userAttributeRepository.save(attribute);
            log.info("Deactivated attribute for user: {}, name: {}", userId, attributeName);
            return true;
        }

        log.warn("Attribute not found for removal: user={}, name={}", userId, attributeName);
        return false;
    }

    /**
     * Sync user attributes from Keycloak
     */
    @Transactional
    public List<UserAttribute> syncUserAttributesFromKeycloak(String userId) {
        log.info("Syncing user attributes from Keycloak for user: {}", userId);

        try {
            Map<String, List<String>> keycloakAttributes = keycloakService.getUserAttributes(userId);
            List<UserAttribute> syncedAttributes = new ArrayList<>();

            for (Map.Entry<String, List<String>> entry : keycloakAttributes.entrySet()) {
                String attributeName = entry.getKey();
                List<String> values = entry.getValue();

                if (values != null && !values.isEmpty()) {
                    // For multiple values, we'll store them as a comma-separated list
                    String attributeValue = values.size() == 1 ? values.get(0) : String.join(",", values);
                    String attributeType = values.size() == 1 ? "STRING" : "LIST";

                    UserAttribute attribute = setUserAttribute(userId, attributeName, attributeValue, 
                                                               attributeType, "KEYCLOAK");
                    attribute.setSyncedFromKeycloak(true);
                    attribute = userAttributeRepository.save(attribute);
                    syncedAttributes.add(attribute);
                }
            }

            log.info("Synced {} attributes from Keycloak for user: {}", syncedAttributes.size(), userId);
            return syncedAttributes;

        } catch (Exception e) {
            log.error("Error syncing attributes from Keycloak for user: {}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Check if user has specific attribute value
     */
    public boolean userHasAttributeValue(String userId, String attributeName, String attributeValue) {
        return userAttributeRepository.userHasAttributeValue(userId, attributeName, attributeValue);
    }

    /**
     * Find users with a specific attribute
     */
    public List<String> findUsersWithAttribute(String attributeName, String attributeValue) {
        return userAttributeRepository.findUserIdsWithAttribute(attributeName, attributeValue);
    }

    /**
     * Get all unique attribute names
     */
    public List<String> getAllAttributeNames() {
        return userAttributeRepository.findAll()
                .stream()
                .filter(UserAttribute::getIsActive)
                .map(UserAttribute::getAttributeName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Get attribute statistics
     */
    public Map<String, Long> getAttributeStatistics() {
        Map<String, Long> stats = new HashMap<>();
        
        List<UserAttribute> allAttributes = findByIsActiveTrueOrderByAttributeName();
        
        // Count by attribute name
        Map<String, Long> countByName = allAttributes.stream()
                .collect(Collectors.groupingBy(UserAttribute::getAttributeName, Collectors.counting()));
        
        // Count by source
        Map<String, Long> countBySource = allAttributes.stream()
                .collect(Collectors.groupingBy(UserAttribute::getSource, Collectors.counting()));
        
        // Count by type
        Map<String, Long> countByType = allAttributes.stream()
                .collect(Collectors.groupingBy(UserAttribute::getAttributeType, Collectors.counting()));
        
        stats.put("total_attributes", (long) allAttributes.size());
        stats.put("unique_attribute_names", (long) countByName.size());
        stats.put("keycloak_synced", countBySource.getOrDefault("KEYCLOAK", 0L));
        stats.put("sentrius_managed", countBySource.getOrDefault("SENTRIUS", 0L));
        
        return stats;
    }

    /**
     * Validate user attributes for ABAC policies
     */
    public boolean validateUserForPolicy(String userId, Map<String, Object> requiredAttributes) {
        if (requiredAttributes == null || requiredAttributes.isEmpty()) {
            return true;
        }

        Map<String, String> userAttributes = getUserAttributesAsMap(userId);
        
        for (Map.Entry<String, Object> required : requiredAttributes.entrySet()) {
            String requiredKey = required.getKey();
            Object requiredValue = required.getValue();
            
            // Special handling for user_id
            if ("user_id".equals(requiredKey)) {
                if (!userId.equals(requiredValue)) {
                    return false;
                }
                continue;
            }
            
            if (!userAttributes.containsKey(requiredKey)) {
                log.debug("User {} missing required attribute: {}", userId, requiredKey);
                return false;
            }
            
            String userValue = userAttributes.get(requiredKey);
            if (!requiredValue.toString().equals(userValue)) {
                log.debug("User {} attribute {} value mismatch: required={}, actual={}", 
                         userId, requiredKey, requiredValue, userValue);
                return false;
            }
        }
        
        return true;
    }

    /**
     * Bulk operation to sync all users' attributes from Keycloak
     */
    @Transactional
    public void syncAllUsersFromKeycloak() {
        log.info("Starting bulk sync of all user attributes from Keycloak");
        
        try {
            // This would typically get all users from the User repository
            // For now, this is a placeholder implementation
            log.warn("Bulk sync not implemented - would need access to User repository");
            
        } catch (Exception e) {
            log.error("Error during bulk sync from Keycloak", e);
        }
    }

    /**
     * Clean up inactive attributes older than specified days
     */
    @Transactional
    public int cleanupInactiveAttributes(int olderThanDays) {
        log.info("Cleaning up inactive attributes older than {} days", olderThanDays);
        
        // This would require additional query in repository
        // For now, return 0 as placeholder
        return 0;
    }

    private List<UserAttribute> findByIsActiveTrueOrderByAttributeName() {
        return userAttributeRepository.findAll()
                .stream()
                .filter(UserAttribute::getIsActive)
                .sorted(Comparator.comparing(UserAttribute::getAttributeName))
                .collect(Collectors.toList());
    }
}