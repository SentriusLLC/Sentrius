package io.sentrius.sso.controllers.api.users;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.users.UserAttributeDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.users.UserAttribute;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.users.UserAttributeService;
import io.sentrius.sso.core.utils.AccessUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/users/attributes")
public class UserAttributeController extends BaseController {

    private final UserAttributeService userAttributeService;

    public UserAttributeController(UserAttributeService userAttributeService, UserService userService, SystemOptions systemOptions, ErrorOutputService errorOutputService) {
        super(userService, systemOptions, errorOutputService);
        this.userAttributeService = userAttributeService;
    }

    /**
     * Get all attributes for the current user
     */
    @GetMapping("/me")
    public ResponseEntity<List<UserAttributeDTO>> getMyAttributes(HttpServletRequest request, HttpServletResponse response) {

        var operatingUser = getOperatingUser(request, response);

        log.debug("Getting attributes for user: {}", operatingUser.getUserId());
        
        try {
            List<UserAttribute> attributes = userAttributeService.getUserAttributes(operatingUser.getUserId());
            List<UserAttributeDTO> attributeDTOs = attributes.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(attributeDTOs);
            
        } catch (Exception e) {
            log.error("Error getting attributes for user: {}", operatingUser.getUserId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all attributes for a specific user (admin endpoint)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<UserAttributeDTO>> getUserAttributes(@PathVariable String userId) {
        log.debug("Getting attributes for user: {}", userId);
        
        try {
            List<UserAttribute> attributes = userAttributeService.getUserAttributes(userId);
            List<UserAttributeDTO> attributeDTOs = attributes.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(attributeDTOs);
            
        } catch (Exception e) {
            log.error("Error getting attributes for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get user attributes as a map
     */
    @GetMapping("/{userId}/map")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, String>> getUserAttributesAsMap(@PathVariable String userId) {
        log.debug("Getting attributes map for user: {}", userId);
        
        try {
            Map<String, String> attributesMap = userAttributeService.getUserAttributesAsMap(userId);
            return ResponseEntity.ok(attributesMap);
            
        } catch (Exception e) {
            log.error("Error getting attributes map for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get a specific user attribute
     */
    @GetMapping("/{userId}/{attributeName}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<UserAttributeDTO> getUserAttribute(
            @PathVariable String userId,
            @PathVariable String attributeName) {
        
        log.debug("Getting attribute {} for user: {}", attributeName, userId);
        
        try {
            Optional<UserAttribute> attributeOpt = userAttributeService.getUserAttribute(userId, attributeName);
            
            if (attributeOpt.isPresent()) {
                UserAttributeDTO attributeDTO = convertToDTO(attributeOpt.get());
                return ResponseEntity.ok(attributeDTO);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error getting attribute {} for user: {}", attributeName, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Set a user attribute
     */
    @PostMapping("/update")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<UserAttributeDTO> setUserAttribute(
            @RequestParam("userId") String userId,
            @RequestBody @Valid UserAttributeDTO attributeDTO,
            HttpServletRequest request, HttpServletResponse response) {
        
        log.info("Setting attribute {} for user: {}", attributeDTO.getAttributeName(), userId);
        
        try {
            var operatingUser = getOperatingUser(request, response);
            String requestingUserId = operatingUser.getUserId();
            
            // Basic authorization - users can only modify their own attributes unless admin
            if (!userId.equals(requestingUserId) && !AccessUtil.canAccess(operatingUser, ApplicationAccessEnum.CAN_MANAGE_APPLICATION)) {
                log.warn("User {} attempted to modify attributes for user {}", requestingUserId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            UserAttribute attribute = userAttributeService.setUserAttribute(
                    userId,
                    attributeDTO.getAttributeName(),
                    attributeDTO.getAttributeValue(),
                    attributeDTO.getAttributeType(),
                    attributeDTO.getSource()
            );
            
            UserAttributeDTO responseDTO = convertToDTO(attribute);
            return ResponseEntity.ok(responseDTO);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid attribute data for user {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error setting attribute {} for user: {}", attributeDTO.getAttributeName(), userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Set multiple user attributes at once
     */
    @PostMapping("/{userId}/bulk")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<UserAttributeDTO>> setUserAttributes(
            @PathVariable String userId,
            @RequestBody Map<String, String> attributes,
            HttpServletRequest request, HttpServletResponse response) {
        
        log.info("Setting {} attributes for user: {}", attributes.size(), userId);
        
        try {
            var operatingUser = getOperatingUser(request, response);
            String requestingUserId = operatingUser.getUserId();
            
            // Basic authorization - users can only modify their own attributes unless admin
            if (!userId.equals(requestingUserId) && !AccessUtil.canAccess(operatingUser, ApplicationAccessEnum.CAN_MANAGE_APPLICATION)) {
                log.warn("User {} attempted to modify attributes for user {}", requestingUserId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            List<UserAttribute> savedAttributes = userAttributeService.setUserAttributes(
                    userId, attributes, "SENTRIUS");
            
            List<UserAttributeDTO> responseDTOs = savedAttributes.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);
            
        } catch (Exception e) {
            log.error("Error setting bulk attributes for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Remove a user attribute
     */
    @DeleteMapping("/{userId}/{attributeName}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Object>> removeUserAttribute(
            @PathVariable String userId,
            @PathVariable String attributeName,
            HttpServletRequest request, HttpServletResponse response) {
        
        log.info("Removing attribute {} for user: {}", attributeName, userId);
        
        try {
            var operatingUser = getOperatingUser(request, response);
            String requestingUserId = operatingUser.getUserId();
            
            // Basic authorization - users can only modify their own attributes unless admin
            if (!userId.equals(requestingUserId) && !AccessUtil.canAccess(operatingUser, ApplicationAccessEnum.CAN_MANAGE_APPLICATION)) {
                log.warn("User {} attempted to remove attributes for user {}", requestingUserId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            boolean success = userAttributeService.removeUserAttribute(userId, attributeName);
            
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("success", success);
            userResponse.put("removed", success);
            
            return ResponseEntity.ok(userResponse);
            
        } catch (Exception e) {
            log.error("Error removing attribute {} for user: {}", attributeName, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Sync user attributes from Keycloak
     */
    @PostMapping("/{userId}/sync/keycloak")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<UserAttributeDTO>> syncFromKeycloak(
            @PathVariable String userId,
            HttpServletRequest request, HttpServletResponse response) {
        
        log.info("Syncing attributes from Keycloak for user: {}", userId);
        
        try {
            var operatingUser = getOperatingUser(request, response);
            String requestingUserId = operatingUser.getUserId();
            
            // Basic authorization - users can sync their own attributes or admin can sync any
            if (!userId.equals(requestingUserId) && !AccessUtil.canAccess(operatingUser, ApplicationAccessEnum.CAN_MANAGE_APPLICATION)) {
                log.warn("User {} attempted to sync attributes for user {}", requestingUserId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            List<UserAttribute> syncedAttributes = userAttributeService.syncUserAttributesFromKeycloak(userId);
            List<UserAttributeDTO> responseDTOs = syncedAttributes.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(responseDTOs);
            
        } catch (Exception e) {
            log.error("Error syncing attributes from Keycloak for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Check if user has specific attribute value
     */
    @GetMapping("/{userId}/check")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<Map<String, Boolean>> checkUserAttribute(
            @PathVariable String userId,
            @RequestParam String attributeName,
            @RequestParam String attributeValue) {
        
        log.debug("Checking if user {} has attribute {}={}", userId, attributeName, attributeValue);
        
        try {
            boolean hasAttribute = userAttributeService.userHasAttributeValue(userId, attributeName, attributeValue);
            
            Map<String, Boolean> response = new HashMap<>();
            response.put("hasAttribute", hasAttribute);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking attribute for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Find users with specific attribute (admin endpoint)
     */
    @GetMapping("/search")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<String>> findUsersWithAttribute(
            @RequestParam String attributeName,
            @RequestParam String attributeValue) {
        
        log.debug("Finding users with attribute {}={}", attributeName, attributeValue);
        
        try {
            List<String> userIds = userAttributeService.findUsersWithAttribute(attributeName, attributeValue);
            return ResponseEntity.ok(userIds);
            
        } catch (Exception e) {
            log.error("Error finding users with attribute", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all unique attribute names (admin endpoint)
     */
    @GetMapping("/names")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<String>> getAllAttributeNames() {
        log.debug("Getting all unique attribute names");
        
        try {
            List<String> attributeNames = userAttributeService.getAllAttributeNames();
            return ResponseEntity.ok(attributeNames);
            
        } catch (Exception e) {
            log.error("Error getting attribute names", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get attribute statistics (admin endpoint)
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Long>> getAttributeStatistics() {
        log.debug("Getting attribute statistics");
        
        try {
            Map<String, Long> stats = userAttributeService.getAttributeStatistics();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting attribute statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Convert UserAttribute entity to DTO
     */
    private UserAttributeDTO convertToDTO(UserAttribute attribute) {
        return UserAttributeDTO.builder()
                .id(attribute.getId())
                .userId(attribute.getUserId())
                .attributeName(attribute.getAttributeName())
                .attributeValue(attribute.getAttributeValue())
                .attributeType(attribute.getAttributeType())
                .source(attribute.getSource())
                .isActive(attribute.getIsActive())
                .createdAt(attribute.getCreatedAt())
                .updatedAt(attribute.getUpdatedAt())
                .syncedFromKeycloak(attribute.getSyncedFromKeycloak())
                .build();
    }

}