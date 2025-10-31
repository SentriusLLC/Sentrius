package io.sentrius.sso.core.services.customattributes;

import io.sentrius.sso.core.model.customattributes.CustomAttributeMapping;
import io.sentrius.sso.core.repository.customattributes.CustomAttributeMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CustomAttributeMappingService {

    private final CustomAttributeMappingRepository repository;

    public CustomAttributeMappingService(CustomAttributeMappingRepository repository) {
        this.repository = repository;
    }

    /**
     * Get all active custom attribute mappings
     */
    public List<CustomAttributeMapping> getAllMappings() {
        log.debug("Getting all custom attribute mappings");
        return repository.findByIsActiveTrue();
    }

    /**
     * Get mappings for a specific endpoint
     */
    public List<CustomAttributeMapping> getMappingsByEndpoint(String endpoint) {
        log.debug("Getting custom attribute mappings for endpoint: {}", endpoint);
        return repository.findByEndpointAndIsActiveTrue(endpoint);
    }

    /**
     * Get a mapping by ID
     */
    public Optional<CustomAttributeMapping> getMappingById(Long id) {
        return repository.findById(id);
    }

    /**
     * Create a new custom attribute mapping
     */
    @Transactional
    public CustomAttributeMapping createMapping(String endpoint, String attributeName, 
                                                String requiredValue, String description) {
        log.info("Creating custom attribute mapping: endpoint={}, attribute={}={}", 
                 endpoint, attributeName, requiredValue);

        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint cannot be empty");
        }
        if (attributeName == null || attributeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Attribute name cannot be empty");
        }
        if (requiredValue == null || requiredValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Required value cannot be empty");
        }

        CustomAttributeMapping mapping = CustomAttributeMapping.builder()
                .endpoint(endpoint.trim())
                .attributeName(attributeName.trim())
                .requiredValue(requiredValue.trim())
                .description(description)
                .isActive(true)
                .build();

        return repository.save(mapping);
    }

    /**
     * Update an existing custom attribute mapping
     */
    @Transactional
    public CustomAttributeMapping updateMapping(Long id, String endpoint, String attributeName,
                                                String requiredValue, String description, Boolean isActive) {
        log.info("Updating custom attribute mapping: {}", id);

        Optional<CustomAttributeMapping> existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            log.warn("Mapping not found: {}", id);
            return null;
        }

        CustomAttributeMapping mapping = existingOpt.get();
        
        if (endpoint != null && !endpoint.trim().isEmpty()) {
            mapping.setEndpoint(endpoint.trim());
        }
        if (attributeName != null && !attributeName.trim().isEmpty()) {
            mapping.setAttributeName(attributeName.trim());
        }
        if (requiredValue != null && !requiredValue.trim().isEmpty()) {
            mapping.setRequiredValue(requiredValue.trim());
        }
        if (description != null) {
            mapping.setDescription(description);
        }
        if (isActive != null) {
            mapping.setIsActive(isActive);
        }

        return repository.save(mapping);
    }

    /**
     * Delete a custom attribute mapping (soft delete by setting isActive to false)
     */
    @Transactional
    public boolean deleteMapping(Long id) {
        log.info("Deleting custom attribute mapping: {}", id);

        Optional<CustomAttributeMapping> mappingOpt = repository.findById(id);
        if (mappingOpt.isEmpty()) {
            log.warn("Mapping not found for deletion: {}", id);
            return false;
        }

        CustomAttributeMapping mapping = mappingOpt.get();
        mapping.setIsActive(false);
        repository.save(mapping);
        
        log.info("Deactivated custom attribute mapping: {}", id);
        return true;
    }

    /**
     * Get all unique endpoints that have custom attribute mappings
     */
    public List<String> getAllEndpoints() {
        log.debug("Getting all unique endpoints");
        return repository.findAllUniqueEndpoints();
    }

    /**
     * Get all unique attribute names used in mappings
     */
    public List<String> getAllAttributeNames() {
        log.debug("Getting all unique attribute names");
        return repository.findAllUniqueAttributeNames();
    }

    /**
     * Get custom attribute strings for an endpoint in annotation format
     * Returns list of strings like "attributeName=requiredValue"
     */
    public List<String> getCustomAttributeStringsForEndpoint(String endpoint) {
        log.debug("Getting custom attribute strings for endpoint: {}", endpoint);
        
        List<CustomAttributeMapping> mappings = getMappingsByEndpoint(endpoint);
        return mappings.stream()
                .map(CustomAttributeMapping::toCustomAttributeString)
                .collect(Collectors.toList());
    }
}
