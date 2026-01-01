package io.sentrius.sso.controllers.api.agents;

import io.sentrius.sso.config.ApiPaths;
import io.sentrius.sso.config.AppConfig;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.AgentRegistrationDTO;
import io.sentrius.sso.core.dto.agents.AgentContextRequestDTO;
import io.sentrius.sso.core.dto.agents.AgentTemplateDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentClientService;
import io.sentrius.sso.core.services.agents.AgentContextService;
import io.sentrius.sso.core.services.agents.AgentLaunchService;
import io.sentrius.sso.core.services.agents.AgentTemplateService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(ApiPaths.API_V1 + "/agent/templates")
public class AgentTemplateController extends BaseController {

    private final AgentTemplateService templateService;
    private final ZeroTrustClientService zeroTrustClientService;
    private final AppConfig appConfig;
    private final ATPLPolicyService atplPolicyService;
    private final AgentLaunchService agentLaunchService;
    private final AgentContextService agentContextService;
    private final AgentClientService agentClientService;

    public AgentTemplateController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AgentTemplateService templateService,
        ZeroTrustClientService zeroTrustClientService,
        AppConfig appConfig,
        ATPLPolicyService atplPolicyService,
        AgentLaunchService agentLaunchService,
        AgentContextService agentContextService,
        AgentClientService agentClientService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.templateService = templateService;
        this.zeroTrustClientService = zeroTrustClientService;
        this.appConfig = appConfig;
        this.atplPolicyService = atplPolicyService;
        this.agentLaunchService = agentLaunchService;
        this.agentContextService = agentContextService;
        this.agentClientService = agentClientService;
    }

    /**
     * Get all enabled templates
     */
    @GetMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<AgentTemplateDTO>> getAllTemplates(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }

        log.info("User {} requested agent templates", operatingUser.getUsername());
        List<AgentTemplateDTO> templates = templateService.getAllEnabledTemplates();
        return ResponseEntity.ok(templates);
    }

    /**
     * Get templates by category
     */
    @GetMapping("/category/{category}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<AgentTemplateDTO>> getTemplatesByCategory(
        @PathVariable String category,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }

        log.info("User {} requested templates for category: {}", operatingUser.getUsername(), category);
        List<AgentTemplateDTO> templates = templateService.getTemplatesByCategory(category);
        return ResponseEntity.ok(templates);
    }

    /**
     * Get a specific template by ID
     */
    @GetMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<AgentTemplateDTO> getTemplate(
        @PathVariable UUID id,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }

        return templateService.getTemplateById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new template
     */
    @PostMapping
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> createTemplate(
        @RequestBody AgentTemplateDTO templateDTO,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            templateDTO.setCreatedBy(operatingUser.getUsername());
            templateDTO.setSystemTemplate(false); // User templates are never system templates

            AgentTemplateDTO created = templateService.createTemplate(templateDTO);
            log.info("User {} created new agent template: {}", operatingUser.getUsername(), created.getName());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            log.error("Error creating agent template", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to create template: " + e.getMessage()));
        }
    }

    /**
     * Update an existing template
     */
    @PutMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> updateTemplate(
        @PathVariable UUID id,
        @RequestBody AgentTemplateDTO templateDTO,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            AgentTemplateDTO updated = templateService.updateTemplate(id, templateDTO);
            log.info("User {} updated agent template: {}", operatingUser.getUsername(), updated.getName());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating agent template", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to update template: " + e.getMessage()));
        }
    }

    /**
     * Delete a template
     */
    @DeleteMapping("/{id}")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> deleteTemplate(
        @PathVariable UUID id,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            templateService.deleteTemplate(id);
            log.info("User {} deleted agent template: {}", operatingUser.getUsername(), id);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Template deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting agent template", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to delete template: " + e.getMessage()));
        }
    }

    /**
     * Build an AgentRegistrationDTO from a template for launcher service
     * This endpoint provides the template configuration in a format suitable for the agent launcher
     */
    @PostMapping("/{id}/prepare-launch")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> prepareLaunch(
        @PathVariable UUID id,
        @RequestParam String agentName,
        @RequestParam(required = false) String agentCallbackUrl,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            AgentTemplateDTO template = templateService.getTemplateById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

            // Build AgentRegistrationDTO with full template configuration
            AgentRegistrationDTO agentDto = AgentRegistrationDTO.builder()
                .agentName(agentName)
                .agentType(template.getAgentType())
                .agentCallbackUrl(agentCallbackUrl != null ? agentCallbackUrl : "")
                .agentTemplateId(id.toString())
                .templateConfiguration(template.getDefaultConfiguration())
                .templateIdentity(template.getIdentity())
                .templatePurpose(template.getPurpose())
                .templateGoals(template.getGoals())
                .templateGuardrails(template.getGuardrails())
                .templateTrustPolicyId(template.getTrustPolicyId())
                .templateLaunchConfiguration(template.getLaunchConfiguration())
                .agentPolicyId(template.getTrustPolicyId() != null ? template.getTrustPolicyId() : "")
                .build();

            log.info("User {} prepared agent launch from template: {} -> agent: {}",
                operatingUser.getUsername(), template.getName(), agentName);

            return ResponseEntity.ok(agentDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error preparing agent launch from template", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to prepare launch: " + e.getMessage()));
        }
    }

    /**
     * Launch an agent from a template
     * This endpoint creates an agent registration and triggers the launcher service automatically
     *
     * @param id Template ID
     * @param agentName Name for the new agent
     * @param agentContextId Optional context ID for the agent (if not provided, will be created from template)
     * @return Launch response with agent details
     */
    @PostMapping("/{id}/launch")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<?> launchFromTemplate(
        @PathVariable UUID id,
        @RequestParam String agentName,
        @RequestParam(required = false) String agentContextId,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            AgentTemplateDTO template = templateService.getTemplateById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));

            log.info("User {} launching agent '{}' from template '{}'",
                operatingUser.getUsername(), agentName, template.getName());

            // Check if agent is already running
            try {
                String status = agentClientService.getAgentPodStatus(
                    appConfig.getSentriusLauncherService(),
                    agentName
                );
                if ("Running".equals(status) || "Pending".equals(status)) {
                    log.info("Agent {} is already running or pending", agentName);
                    return ResponseEntity.ok(Map.of(
                        "status", "already_exists",
                        "message", "Agent is already running or pending",
                        "agentName", agentName
                    ));
                }
            } catch (Exception e) {
                log.debug("Agent status check failed (agent may not exist yet): {}", e.getMessage());
            }

            // Store agent context in database if not provided
            String contextId = agentContextId;
            if (contextId == null || contextId.isEmpty()) {
                log.info("Creating agent context from template for agent '{}'", agentName);

                // Build context string with all template information
                StringBuilder contextBuilder = new StringBuilder();
                contextBuilder.append("# Agent Configuration from Template: ").append(template.getName()).append("\n\n");

                appendSectionIfPresent(contextBuilder, "Purpose", template.getPurpose(), false);
                appendSectionIfPresent(contextBuilder, "Goals", template.getGoals(), false);
                appendSectionIfPresent(contextBuilder, "Configuration", template.getDefaultConfiguration(), true);
                appendSectionIfPresent(contextBuilder, "Identity", template.getIdentity(), true);
                appendSectionIfPresent(contextBuilder, "Guardrails", template.getGuardrails(), true);
                appendSectionIfPresent(contextBuilder, "Launch Configuration", template.getLaunchConfiguration(), true);
                appendSectionIfPresent(contextBuilder, "Trust Policy ID", template.getTrustPolicyId(), false);

                // Create agent context with template information
                AgentContextRequestDTO contextRequest =
                    AgentContextRequestDTO.builder()
                        .name(agentName)
                        .description("Agent context created from template: " + template.getName())
                        .context(contextBuilder.toString())
                        .policyId(template.getTrustPolicyId())
                        .build();

                AgentContext savedContext = agentContextService.create(contextRequest);

                contextId = savedContext.getId().toString();
                log.info("Created agent context with ID: {} for agent '{}'", contextId, agentName);
            }

            // Build AgentRegistrationDTO with context ID instead of embedded template data
            AgentRegistrationDTO agentDto = AgentRegistrationDTO.builder()
                .agentName(agentName)
                .agentType(template.getAgentType())
                .agentCallbackUrl("")
                .clientId(agentName)  // Set clientId to match agentName for policy caching
                .agentTemplateId(id.toString())
                .agentContextId(contextId)
                .agentPolicyId(template.getTrustPolicyId() != null ? template.getTrustPolicyId() : "")
                .build();

            // Cache the policy if it exists
            if (template.getTrustPolicyId() != null && !template.getTrustPolicyId().isEmpty()) {
                var latest = atplPolicyService.getLatestPolicyEntity(template.getTrustPolicyId());
                if (latest.isPresent()) {
                    log.info("Caching policy {} for agent {}", template.getTrustPolicyId(), agentName);
                    atplPolicyService.cachePolicy(agentDto.getClientId(), template.getTrustPolicyId());
                } else {
                    log.warn("Policy {} not found, skipping cache", template.getTrustPolicyId());
                }
            }

            // Call the launcher service
            zeroTrustClientService.callAuthenticatedPostOnApi(
                appConfig.getSentriusLauncherService(),
                "agent/launcher/create",
                agentDto
            );

            // Record the agent launch with the context ID
            try {
                UUID contextUuid = UUID.fromString(contextId);
                String launchedBy = operatingUser.getUserId();
                String parameters = String.format(
                    "agentType=%s,templateId=%s,policyId=%s",
                    template.getAgentType(),
                    id.toString(),
                    template.getTrustPolicyId() != null ? template.getTrustPolicyId() : "none"
                );

                UUID launchId = agentLaunchService.recordLaunch(
                    agentName,
                    contextUuid,
                    launchedBy,
                    parameters
                );

                log.info("Recorded agent launch: launchId={}, contextId={}, agentName={}",
                    launchId, contextUuid, agentName);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid contextId '{}', skipping launch record: {}", contextId, e.getMessage());
            } catch (Exception e) {
                log.warn("Failed to record agent launch (non-critical): {}", e.getMessage());
            }

            log.info("Successfully launched agent '{}' from template '{}'", agentName, template.getName());

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Agent launched successfully",
                "agentName", agentName,
                "templateId", id.toString(),
                "templateName", template.getName(),
                "agentType", template.getAgentType()
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (ZtatException e) {
            log.error("Error calling launcher service", e);
            return ResponseEntity.status(503)
                .body(Map.of("error", "Failed to contact launcher service: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error launching agent from template", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to launch agent: " + e.getMessage()));
        }
    }

    /**
     * Helper method to append a section to the context string builder if content is present
     *
     * @param builder The StringBuilder to append to
     * @param title The section title
     * @param content The content to append
     * @param isJson Whether to wrap content in JSON code blocks
     */
    private void appendSectionIfPresent(StringBuilder builder, String title, String content, boolean isJson) {
        if (content != null && !content.trim().isEmpty()) {
            builder.append("## ").append(title).append("\n");
            if (isJson) {
                builder.append("```json\n").append(content).append("\n```\n\n");
            } else {
                builder.append(content).append("\n\n");
            }
        }
    }
}