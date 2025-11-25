package io.sentrius.sso.core.services;

import io.sentrius.sso.core.model.ConfigurationOption;
import io.sentrius.sso.core.repository.ConfigurationOptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing property overrides stored in the database.
 * Provides CRUD operations for configuration properties with database persistence.
 * Supports pod-specific and global property overrides.
 */
@Slf4j
@Service
public class PropertyOverrideService {

    private final ConfigurationOptionRepository configurationOptionRepository;
    private final ConfigurableEnvironment environment;
    
    // Security-sensitive property patterns that should not be overridden
    private static final Set<String> SECURITY_PROPERTY_PATTERNS = Set.of(
        "password",
        "secret",
        "keystore",
        "token",
        "credential",
        "private.key",
        "secret.key",
        "api.key",
        "encryption.key",
        "oauth2.client.registration",
        "security.oauth2.resourceserver"
    );

    public PropertyOverrideService(ConfigurationOptionRepository configurationOptionRepository,
                                   ConfigurableEnvironment environment) {
        this.configurationOptionRepository = configurationOptionRepository;
        this.environment = environment;
    }

    /**
     * Get all properties from application.properties with their current values.
     * Properties are read from database first, then fall back to file values.
     * Security-sensitive properties are excluded.
     * This method returns global overrides (no pod specified).
     * 
     * @return Map of property names to PropertyInfo objects
     */
    public Map<String, PropertyInfo> getAllProperties() {
        return getAllProperties(null);
    }

    /**
     * Get all properties for a specific pod with their current values.
     * Pod-specific overrides take precedence over global overrides, which take precedence over file values.
     * Security-sensitive properties are excluded.
     * 
     * @param podName The pod name to get properties for, or null for global properties
     * @return Map of property names to PropertyInfo objects
     */
    public Map<String, PropertyInfo> getAllProperties(String podName) {
        Map<String, PropertyInfo> properties = new LinkedHashMap<>();
        
        // Get all properties from Spring environment
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerablePropertySource = (EnumerablePropertySource<?>) propertySource;
                for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                    // Skip security-sensitive properties
                    if (isSecuritySensitive(propertyName)) {
                        continue;
                    }
                    
                    if (!properties.containsKey(propertyName)) {
                        String fileValue = environment.getProperty(propertyName);
                        Optional<ConfigurationOption> dbOverride = findOverride(podName, propertyName);
                        
                        PropertyInfo info = PropertyInfo.builder()
                            .propertyName(propertyName)
                            .podName(dbOverride.map(ConfigurationOption::getPodName).orElse(null))
                            .fileValue(fileValue)
                            .databaseValue(dbOverride.map(ConfigurationOption::getConfigurationValue).orElse(null))
                            .currentValue(dbOverride.map(ConfigurationOption::getConfigurationValue).orElse(fileValue))
                            .hasOverride(dbOverride.isPresent())
                            .build();
                        
                        properties.put(propertyName, info);
                    }
                }
            }
        }
        
        // Also include any database-only overrides for this pod
        List<ConfigurationOption> allDbOptions = podName != null 
            ? configurationOptionRepository.findAllByPodName(podName)
            : configurationOptionRepository.findAllGlobal();
        for (ConfigurationOption option : allDbOptions) {
            if (!isSecuritySensitive(option.getConfigurationName()) && 
                !properties.containsKey(option.getConfigurationName())) {
                PropertyInfo info = PropertyInfo.builder()
                    .propertyName(option.getConfigurationName())
                    .podName(option.getPodName())
                    .fileValue(null)
                    .databaseValue(option.getConfigurationValue())
                    .currentValue(option.getConfigurationValue())
                    .hasOverride(true)
                    .build();
                properties.put(option.getConfigurationName(), info);
            }
        }
        
        return properties;
    }

    /**
     * Find an override, preferring pod-specific over global.
     * 
     * @param podName The pod name (can be null for global lookup)
     * @param propertyName The property name
     * @return The configuration option if found
     */
    private Optional<ConfigurationOption> findOverride(String podName, String propertyName) {
        if (podName != null) {
            // Try pod-specific first
            Optional<ConfigurationOption> podOverride = 
                configurationOptionRepository.findLatestByPodNameAndConfigurationName(podName, propertyName);
            if (podOverride.isPresent()) {
                return podOverride;
            }
            // Fall back to global
            return configurationOptionRepository.findLatestGlobalByConfigurationName(propertyName);
        }
        // Global lookup only
        return configurationOptionRepository.findLatestGlobalByConfigurationName(propertyName);
    }

    /**
     * Get a specific property value (database override takes precedence).
     * Uses global overrides (no pod specified).
     * 
     * @param propertyName The property name
     * @return The property value or null if not found
     */
    public String getProperty(String propertyName) {
        return getProperty(null, propertyName);
    }

    /**
     * Get a specific property value for a pod (pod-specific override takes precedence over global).
     * 
     * @param podName The pod name, or null for global lookup
     * @param propertyName The property name
     * @return The property value or null if not found
     */
    public String getProperty(String podName, String propertyName) {
        if (isSecuritySensitive(propertyName)) {
            log.warn("Attempted to access security-sensitive property: {}", propertyName);
            return null;
        }
        
        Optional<ConfigurationOption> dbOverride = findOverride(podName, propertyName);
        
        if (dbOverride.isPresent()) {
            return dbOverride.get().getConfigurationValue();
        }
        
        return environment.getProperty(propertyName);
    }

    /**
     * Set or update a global property override in the database.
     * 
     * @param propertyName The property name
     * @param value The property value
     * @return The saved ConfigurationOption
     */
    @Transactional
    public ConfigurationOption setPropertyOverride(String propertyName, String value) {
        return setPropertyOverride(null, propertyName, value);
    }

    /**
     * Set or update a property override for a specific pod in the database.
     * 
     * @param podName The pod name, or null for global override
     * @param propertyName The property name
     * @param value The property value
     * @return The saved ConfigurationOption
     */
    @Transactional
    public ConfigurationOption setPropertyOverride(String podName, String propertyName, String value) {
        if (isSecuritySensitive(propertyName)) {
            throw new SecurityException("Cannot override security-sensitive property: " + propertyName);
        }
        
        log.info("Setting property override for '{}' (pod: {})", propertyName, podName != null ? podName : "global");
        
        ConfigurationOption option = ConfigurationOption.builder()
            .podName(podName)
            .configurationName(propertyName)
            .configurationValue(value)
            .build();
        
        return configurationOptionRepository.save(option);
    }

    /**
     * Remove a global property override from the database.
     * The property will revert to its file value.
     * 
     * @param propertyName The property name
     */
    @Transactional
    public void removePropertyOverride(String propertyName) {
        removePropertyOverride(null, propertyName);
    }

    /**
     * Remove a property override for a specific pod from the database.
     * The property will fall back to global override or file value.
     * 
     * @param podName The pod name, or null for global override
     * @param propertyName The property name
     */
    @Transactional
    public void removePropertyOverride(String podName, String propertyName) {
        if (isSecuritySensitive(propertyName)) {
            throw new SecurityException("Cannot remove security-sensitive property: " + propertyName);
        }
        
        Optional<ConfigurationOption> existing = podName != null
            ? configurationOptionRepository.findLatestByPodNameAndConfigurationName(podName, propertyName)
            : configurationOptionRepository.findLatestGlobalByConfigurationName(propertyName);
        
        existing.ifPresent(option -> {
            log.info("Removing property override for '{}' (pod: {})", propertyName, podName != null ? podName : "global");
            configurationOptionRepository.delete(option);
        });
    }

    /**
     * Get all configuration options for a specific pod.
     * 
     * @param podName The pod name
     * @return List of configuration options for the pod
     */
    public List<ConfigurationOption> getAllConfigurationsForPod(String podName) {
        if (podName == null) {
            return configurationOptionRepository.findAllGlobal();
        }
        return configurationOptionRepository.findAllByPodName(podName);
    }

    /**
     * Check if a property name contains security-sensitive keywords.
     * 
     * @param propertyName The property name to check
     * @return true if the property is security-sensitive
     */
    private boolean isSecuritySensitive(String propertyName) {
        if (propertyName == null) {
            return false;
        }
        
        String lowerName = propertyName.toLowerCase();
        return SECURITY_PROPERTY_PATTERNS.stream()
            .anyMatch(lowerName::contains);
    }

    /**
     * Data class representing information about a property.
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class PropertyInfo {
        private final String propertyName;
        private final String podName;
        private final String fileValue;
        private final String databaseValue;
        private final String currentValue;
        private final boolean hasOverride;
    }
}
