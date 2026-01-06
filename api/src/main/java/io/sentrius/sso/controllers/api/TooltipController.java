package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.tooltip.TooltipChatRequest;
import io.sentrius.sso.core.dto.tooltip.TooltipChatResponse;
import io.sentrius.sso.core.dto.tooltip.TooltipDescribeRequest;
import io.sentrius.sso.core.dto.tooltip.TooltipDescribeResponse;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.verbs.Endpoint;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.tooltip.CodebaseIndexingService;
import io.sentrius.sso.core.services.tooltip.TooltipService;
import io.sentrius.sso.core.utils.AccessUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for AI-powered tooltip and contextual help features.
 * Provides endpoints for getting descriptions of UI elements and chat-based assistance.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tooltip")
public class TooltipController extends BaseController {

    private final TooltipService tooltipService;
    private final CodebaseIndexingService indexingService;

    public TooltipController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            TooltipService tooltipService,
            CodebaseIndexingService indexingService) {
        super(userService, systemOptions, errorOutputService);
        this.tooltipService = tooltipService;
        this.indexingService = indexingService;
    }

    /**
     * Get AI-powered description for a UI element.
     * Searches indexed codebase and documentation to provide contextual tooltips.
     *
     * @param request Element context information from the frontend
     * @param httpRequest HTTP request for authentication
     * @param httpResponse HTTP response
     * @return AI-generated description of the element
     */
    @PostMapping("/describe")
    @Endpoint(description = "Get AI-powered description for a UI element")
    @LimitAccess(applicationAccess = ApplicationAccessEnum.CAN_LOG_IN)
    public ResponseEntity<TooltipDescribeResponse> describe(
            @RequestBody TooltipDescribeRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        try {
            User operatingUser = getOperatingUser(httpRequest, httpResponse);
            log.info("Tooltip describe request from user: {}", operatingUser.getUserId());

            // Validate request
            if (request.getContext() == null) {
                return ResponseEntity.badRequest()
                        .body(TooltipDescribeResponse.builder()
                                .description("Invalid request: context is required")
                                .error("Context is required")
                                .success(false)
                                .build());
            }

            // Build token DTO for LLM service
            TokenDTO tokenDTO = TokenDTO.builder().communicationId(UUID.randomUUID().toString()).build();
            // Token will be populated from security context by LLM service

            // Generate description
            TooltipDescribeResponse response = tooltipService.describeElement(request, tokenDTO);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing tooltip describe request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TooltipDescribeResponse.builder()
                            .description("An error occurred while generating the description.")
                            .error(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    /**
     * Chat endpoint for conversational assistance about UI elements and features.
     *
     * @param request Chat message and optional element context
     * @param httpRequest HTTP request for authentication
     * @param httpResponse HTTP response
     * @return AI-generated chat response
     */
    @PostMapping("/chat")
    @Endpoint(description = "Chat with AI assistant about UI elements and features")
    @LimitAccess(applicationAccess = ApplicationAccessEnum.CAN_LOG_IN)
    public ResponseEntity<TooltipChatResponse> chat(
            @RequestBody TooltipChatRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        try {
            User operatingUser = getOperatingUser(httpRequest, httpResponse);
            log.info("Tooltip chat request from user: {}", operatingUser.getUserId());

            // Validate request
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(TooltipChatResponse.builder()
                                .response("Invalid request: message is required")
                                .error("Message is required")
                                .success(false)
                                .build());
            }

            // Build token DTO for LLM service
            TokenDTO tokenDTO = TokenDTO.builder().communicationId(UUID.randomUUID().toString()).build();
            // Token will be populated from security context by LLM service

            // Generate chat response
            TooltipChatResponse response = tooltipService.chat(request, tokenDTO);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing tooltip chat request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TooltipChatResponse.builder()
                            .response("An error occurred while processing your message.")
                            .error(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    /**
     * Trigger manual indexing of codebase and documentation.
     * Requires admin/system management permissions.
     *
     * @param httpRequest HTTP request for authentication
     * @param httpResponse HTTP response
     * @return Indexing result with statistics
     */
    @PostMapping("/admin/index")
    @Endpoint(description = "Trigger manual indexing of codebase and documentation")
    @LimitAccess(applicationAccess = ApplicationAccessEnum.CAN_MANAGE_APPLICATION)
    public ResponseEntity<Map<String, Object>> triggerIndexing(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        
        try {
            User operatingUser = getOperatingUser(httpRequest, httpResponse);
            
            // Check if user has admin/system permissions
            if (!AccessUtil.canAccess(operatingUser, SSHAccessEnum.CAN_MANAGE_SYSTEMS)) {
                log.warn("Non-admin user {} attempted to trigger indexing", operatingUser.getUserId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "success", false,
                                "error", "Admin privileges required to trigger indexing"
                        ));
            }
            
            log.info("Indexing triggered by admin user: {}", operatingUser.getUserId());
            
            // Run indexing
            CodebaseIndexingService.IndexingResult result = indexingService.indexCodebase();
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("totalFiles", result.getTotalFiles());
            response.put("successCount", result.getSuccessCount());
            response.put("errorCount", result.getErrorCount());
            
            if (result.getErrors() != null && !result.getErrors().isEmpty()) {
                response.put("errors", result.getErrors());
            }
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error triggering indexing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Failed to trigger indexing: " + e.getMessage()
                    ));
        }
    }
}
