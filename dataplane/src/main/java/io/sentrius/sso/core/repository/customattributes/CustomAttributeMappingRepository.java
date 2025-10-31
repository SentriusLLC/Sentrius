package io.sentrius.sso.core.repository.customattributes;

import io.sentrius.sso.core.model.customattributes.CustomAttributeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomAttributeMappingRepository extends JpaRepository<CustomAttributeMapping, Long> {

    /**
     * Find all active mappings for a specific endpoint
     */
    List<CustomAttributeMapping> findByEndpointAndIsActiveTrue(String endpoint);

    /**
     * Find all active mappings
     */
    List<CustomAttributeMapping> findByIsActiveTrue();

    /**
     * Find all mappings for a specific endpoint (including inactive)
     */
    List<CustomAttributeMapping> findByEndpoint(String endpoint);

    /**
     * Find all active mappings by attribute name
     */
    List<CustomAttributeMapping> findByAttributeNameAndIsActiveTrue(String attributeName);

    /**
     * Get all unique endpoints
     */
    @Query("SELECT DISTINCT m.endpoint FROM CustomAttributeMapping m WHERE m.isActive = true")
    List<String> findAllUniqueEndpoints();

    /**
     * Get all unique attribute names
     */
    @Query("SELECT DISTINCT m.attributeName FROM CustomAttributeMapping m WHERE m.isActive = true")
    List<String> findAllUniqueAttributeNames();
}
