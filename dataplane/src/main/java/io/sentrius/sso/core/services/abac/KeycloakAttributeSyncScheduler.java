package io.sentrius.sso.core.services.abac;

import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled task to sync user attributes from Keycloak to the ABAC system.
 * Runs periodically to ensure ABAC attribute assignments stay in sync with Keycloak.
 * 
 * Note: This is a placeholder scheduler. Full implementation requires:
 * 1. Access to Keycloak user list
 * 2. Batch processing for large user bases
 * 3. Error handling and retry logic
 * 4. Monitoring and metrics
 */
@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "sentrius.abac.keycloak-sync.enabled", havingValue = "true", matchIfMissing = false)
public class KeycloakAttributeSyncScheduler {

    private final AttributeManagementService attributeManagementService;
    private final KeycloakService keycloakService;
    private volatile String lastSyncTime = "Never";
    private volatile boolean syncEnabled;
    private volatile int lastSyncUserCount = 0;
    private volatile int lastSyncAttributeCount = 0;

    @Value("${sentrius.abac.keycloak-sync.enabled:false}")
    private boolean configuredEnabled;

    @Value("${sentrius.abac.keycloak-sync.batch-size:100}")
    private int batchSize;

    public KeycloakAttributeSyncScheduler(AttributeManagementService attributeManagementService,
                                         KeycloakService keycloakService) {
        this.attributeManagementService = attributeManagementService;
        this.keycloakService = keycloakService;
        this.syncEnabled = true; // If bean is created, sync is enabled
    }

    /**
     * Sync user attributes from Keycloak every hour.
     * Configurable via: sentrius.abac.keycloak-sync.cron
     */
    @Scheduled(cron = "${sentrius.abac.keycloak-sync.cron:0 0 * * * ?}")  // Every hour by default
    public void syncUserAttributesFromKeycloak() {
        log.info("Keycloak attribute sync triggered (scheduled)");
        
        try {
            int syncedUsers = 0;
            int totalAttributes = 0;
            
            // Get users from Keycloak with pagination
            int first = 0;
            List<UserRepresentation> users;
            
            do {
                users = keycloakService.getUsers(first, batchSize);
                log.debug("Processing batch of {} users starting at index {}", users.size(), first);
                
                for (UserRepresentation user : users) {
                    try {
                        String userId = user.getUsername();
                        Map<String, String> attributes = new HashMap<>();
                        
                        // Extract relevant attributes
                        if (user.getAttributes() != null) {
                            user.getAttributes().forEach((key, values) -> {
                                log.info("Found attribute {} for user {}", key, user.getUsername());
                                if (!values.isEmpty() && shouldSyncAttribute(key)) {
                                    extractAttributes(attributes, key, values);
                                }
                            });
                        }
                        
                        if (!attributes.isEmpty()) {
                            attributeManagementService.syncUserAttributesFromKeycloak(userId, attributes);
                            syncedUsers++;
                            totalAttributes += attributes.size();
                        }
                        
                    } catch (Exception e) {
                        log.warn("Failed to sync attributes for user {}: {}", user.getUsername(), e.getMessage());
                    }
                }
                
                first += users.size();
            } while (users.size() == batchSize); // Continue if we got a full batch
            
            lastSyncUserCount = syncedUsers;
            lastSyncAttributeCount = totalAttributes;
            updateLastSyncTime();
            log.info("Keycloak sync completed: {} users, {} attributes", syncedUsers, totalAttributes);
            
        } catch (Exception e) {
            log.error("Keycloak attribute sync failed", e);
        }
    }

    private void extractAttributes(Map<String, String> attributes, String key, List<String> values) {
        values.forEach(value -> {
            if (value.contains("=")) {
                String[] parts = value.split("=", 2);
                attributes.put(parts[0], parts[1]);
            } else {
                attributes.put(key, value);
            }
        });
    }

    /**
     * Determine if an attribute should be synced based on its name.
     * Filters out internal Keycloak attributes.
     * 
     * @param attributeName The attribute name to check
     * @return true if the attribute should be synced
     */
    private boolean shouldSyncAttribute(String attributeName) {
        // Don't sync internal Keycloak attributes
        if (attributeName.equalsIgnoreCase("attributes")){
            return true;
        }
        return false;
    }

    /**
     * Manual trigger for Keycloak sync.
     * Can be called via management endpoint or programmatically.
     */
    public void triggerManualSync() {
        log.info("Manual Keycloak attribute sync triggered");
        syncUserAttributesFromKeycloak();
    }
    
    /**
     * Sync attributes for a specific user.
     * This is the method to use for individual user attribute sync.
     * 
     * @param userId The user ID
     * @param attributes Map of attribute name to value from Keycloak
     */
    public void syncUserAttributes(String userId, java.util.Map<String, String> attributes) {
        log.info("Syncing {} attributes for user {}", attributes.size(), userId);
        attributeManagementService.syncUserAttributesFromKeycloak(userId, attributes);
        updateLastSyncTime();
    }

    /**
     * Sync all users from Keycloak (called from UI)
     */
    public void syncAllUsersFromKeycloak() {
        log.info("Sync all users from Keycloak triggered");
        syncUserAttributesFromKeycloak();
    }

    /**
     * Sync specific user from Keycloak (called from UI)
     */
    public void syncUserFromKeycloak(String userId) {
        log.info("Sync user {} from Keycloak triggered", userId);
        
        try {
            UserRepresentation user = keycloakService.getUser(userId);
            
            if (user == null) {
                log.warn("User {} not found in Keycloak", userId);
                return;
            }
            
            Map<String, String> attributes = new HashMap<>();
            
            // Extract relevant attributes
            if (user.getAttributes() != null) {
                user.getAttributes().forEach((key, values) -> {
                    log.info("Found attribute {} for user {}", key, user.getUsername());
                    if (!values.isEmpty() && shouldSyncAttribute(key)) {
                        extractAttributes(attributes, key, values);
                    }
                });
            }
            
            if (!attributes.isEmpty()) {
                attributeManagementService.syncUserAttributesFromKeycloak(userId, attributes);
                log.info("Successfully synced {} attributes for user {}", attributes.size(), userId);
            } else {
                log.info("No attributes to sync for user {}", userId);
            }
            
        } catch (Exception e) {
            log.error("Failed to sync user {} from Keycloak", userId, e);
        }
    }

    /**
     * Get last sync time
     */
    public String getLastSyncTime() {
        return lastSyncTime;
    }

    /**
     * Get last sync statistics
     */
    public Map<String, Object> getLastSyncStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("lastSyncTime", lastSyncTime);
        stats.put("syncedUsers", lastSyncUserCount);
        stats.put("syncedAttributes", lastSyncAttributeCount);
        return stats;
    }

    /**
     * Check if sync is enabled
     */
    public boolean isSyncEnabled() {
        return syncEnabled && configuredEnabled;
    }

    /**
     * Update last sync time
     */
    private void updateLastSyncTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.lastSyncTime = LocalDateTime.now().format(formatter);
    }
}
