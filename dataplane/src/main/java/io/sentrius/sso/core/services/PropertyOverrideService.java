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
     * 
     * @return Map of property names to PropertyInfo objects
     */
    public Map<String, PropertyInfo> getAllProperties() {
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
                        Optional<ConfigurationOption> dbOverride = 
                            configurationOptionRepository.findLatestByConfigurationName(propertyName);
                        
                        PropertyInfo info = PropertyInfo.builder()
                            .propertyName(propertyName)
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
        
        // Also include any database-only overrides
        List<ConfigurationOption> allDbOptions = configurationOptionRepository.findAll();
        for (ConfigurationOption option : allDbOptions) {
            if (!isSecuritySensitive(option.getConfigurationName()) && 
                !properties.containsKey(option.getConfigurationName())) {
                PropertyInfo info = PropertyInfo.builder()
                    .propertyName(option.getConfigurationName())
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
     * Get a specific property value (database override takes precedence).
     * 
     * @param propertyName The property name
     * @return The property value or null if not found
     */
    public String getProperty(String propertyName) {
        if (isSecuritySensitive(propertyName)) {
            log.warn("Attempted to access security-sensitive property: {}", propertyName);
            return null;
        }
        
        Optional<ConfigurationOption> dbOverride = 
            configurationOptionRepository.findLatestByConfigurationName(propertyName);
        
        if (dbOverride.isPresent()) {
            return dbOverride.get().getConfigurationValue();
        }
        
        return environment.getProperty(propertyName);
    }

    /**
     * Set or update a property override in the database.
     * 
     * @param propertyName The property name
     * @param value The property value
     * @return The saved ConfigurationOption
     */
    @Transactional
    public ConfigurationOption setPropertyOverride(String propertyName, String value) {
        if (isSecuritySensitive(propertyName)) {
            throw new SecurityException("Cannot override security-sensitive property: " + propertyName);
        }
        
        log.info("Setting property override for '{}'", propertyName);
        
        ConfigurationOption option = ConfigurationOption.builder()
            .configurationName(propertyName)
            .configurationValue(value)
            .build();
        
        return configurationOptionRepository.save(option);
    }

    /**
     * Remove a property override from the database.
     * The property will revert to its file value.
     * 
     * @param propertyName The property name
     */
    @Transactional
    public void removePropertyOverride(String propertyName) {
        if (isSecuritySensitive(propertyName)) {
            throw new SecurityException("Cannot remove security-sensitive property: " + propertyName);
        }
        
        Optional<ConfigurationOption> existing = 
            configurationOptionRepository.findLatestByConfigurationName(propertyName);
        
        existing.ifPresent(option -> {
            log.info("Removing property override for '{}'", propertyName);
            configurationOptionRepository.delete(option);
        });
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
        private final String fileValue;
        private final String databaseValue;
        private final String currentValue;
        private final boolean hasOverride;
    }
}
