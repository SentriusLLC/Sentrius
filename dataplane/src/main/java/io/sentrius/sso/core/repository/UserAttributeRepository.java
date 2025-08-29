package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.users.UserAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface UserAttributeRepository extends JpaRepository<UserAttribute, Long> {

    // Find by user ID
    List<UserAttribute> findByUserIdAndIsActiveTrue(String userId);
    
    // Find by user ID and attribute name
    Optional<UserAttribute> findByUserIdAndAttributeNameAndIsActiveTrue(String userId, String attributeName);
    
    // Find by attribute name across all users
    List<UserAttribute> findByAttributeNameAndIsActiveTrue(String attributeName);
    
    // Find by attribute value
    List<UserAttribute> findByAttributeValueAndIsActiveTrue(String attributeValue);
    
    // Find by source
    List<UserAttribute> findBySourceAndIsActiveTrue(String source);
    
    // Find Keycloak synced attributes
    List<UserAttribute> findBySyncedFromKeycloakTrueAndIsActiveTrue();
    
    // Find by user and source
    List<UserAttribute> findByUserIdAndSourceAndIsActiveTrue(String userId, String source);
    
    // Search attributes by name pattern
    @Query("SELECT ua FROM UserAttribute ua WHERE ua.attributeName LIKE %:namePattern% AND ua.isActive = true")
    List<UserAttribute> findByAttributeNameContaining(@Param("namePattern") String namePattern);
    
    // Get user attributes as map
    @Query("SELECT NEW map(ua.attributeName as name, ua.attributeValue as value) " +
           "FROM UserAttribute ua WHERE ua.userId = :userId AND ua.isActive = true")
    List<Map<String, String>> getUserAttributesAsMap(@Param("userId") String userId);
    
    // Check if user has specific attribute value
    @Query("SELECT COUNT(ua) > 0 FROM UserAttribute ua WHERE " +
           "ua.userId = :userId AND ua.attributeName = :name AND ua.attributeValue = :value AND ua.isActive = true")
    boolean userHasAttributeValue(@Param("userId") String userId, 
                                  @Param("name") String attributeName, 
                                  @Param("value") String attributeValue);
    
    // Find users with specific attribute
    @Query("SELECT DISTINCT ua.userId FROM UserAttribute ua WHERE " +
           "ua.attributeName = :name AND ua.attributeValue = :value AND ua.isActive = true")
    List<String> findUserIdsWithAttribute(@Param("name") String attributeName, @Param("value") String attributeValue);
    
    // Count attributes by user
    long countByUserIdAndIsActiveTrue(String userId);
    
    // Count attributes by name
    long countByAttributeNameAndIsActiveTrue(String attributeName);
    
    // Delete by user and attribute name
    void deleteByUserIdAndAttributeName(String userId, String attributeName);
}