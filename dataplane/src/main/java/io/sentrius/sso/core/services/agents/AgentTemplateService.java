package io.sentrius.sso.core.services.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.dto.agents.AgentTemplateDTO;
import io.sentrius.sso.core.model.agents.AgentTemplate;
import io.sentrius.sso.core.repository.AgentTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentTemplateService {

    private final AgentTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public AgentTemplateService(AgentTemplateRepository templateRepository, ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Get all enabled templates
     */
    @Transactional(readOnly = true)
    public List<AgentTemplateDTO> getAllEnabledTemplates() {
        return templateRepository.findByEnabledTrueOrderByDisplayOrderAsc().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get templates by category
     */
    @Transactional(readOnly = true)
    public List<AgentTemplateDTO> getTemplatesByCategory(String category) {
        return templateRepository.findByCategoryAndEnabledTrueOrderByDisplayOrderAsc(category).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get template by ID
     */
    @Transactional(readOnly = true)
    public Optional<AgentTemplateDTO> getTemplateById(UUID id) {
        return templateRepository.findById(id).map(this::toDTO);
    }

    /**
     * Get template by name
     */
    @Transactional(readOnly = true)
    public Optional<AgentTemplateDTO> getTemplateByName(String name) {
        return templateRepository.findByName(name).map(this::toDTO);
    }

    /**
     * Create a new template
     */
    @Transactional
    public AgentTemplateDTO createTemplate(AgentTemplateDTO dto) {
        AgentTemplate template = fromDTO(dto);
        template = templateRepository.save(template);
        log.info("Created new agent template: {}", template.getName());
        return toDTO(template);
    }

    /**
     * Update an existing template
     */
    @Transactional
    public AgentTemplateDTO updateTemplate(UUID id, AgentTemplateDTO dto) {
        AgentTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        
        // Don't allow modifying system templates
        if (template.isSystemTemplate()) {
            throw new IllegalStateException("Cannot modify system templates");
        }
        
        template.setName(dto.getName());
        template.setDescription(dto.getDescription());
        template.setAgentType(dto.getAgentType());
        template.setIcon(dto.getIcon());
        template.setCategory(dto.getCategory());
        template.setDefaultConfiguration(dto.getDefaultConfiguration());
        template.setIdentity(dto.getIdentity());
        template.setPurpose(dto.getPurpose());
        template.setGoals(dto.getGoals());
        template.setGuardrails(dto.getGuardrails());
        template.setTrustPolicyId(dto.getTrustPolicyId());
        template.setLaunchConfiguration(dto.getLaunchConfiguration());
        template.setEnabled(dto.isEnabled());
        template.setDisplayOrder(dto.getDisplayOrder());
        
        template = templateRepository.save(template);
        log.info("Updated agent template: {}", template.getName());
        return toDTO(template);
    }

    /**
     * Delete a template
     */
    @Transactional
    public void deleteTemplate(UUID id) {
        AgentTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        
        // Don't allow deleting system templates
        if (template.isSystemTemplate()) {
            throw new IllegalStateException("Cannot delete system templates");
        }
        
        templateRepository.delete(template);
        log.info("Deleted agent template: {}", template.getName());
    }

    /**
     * Initialize default system templates if they don't exist
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeDefaultTemplates() {
        log.info("Initializing default agent templates...");
        
        // Chat Assistant Template
        createSystemTemplateIfNotExists(
            "Chat Assistant",
            "Interactive chat agent for Q&A and task assistance",
            "chat",
            "fa-comments",
            "Communication",
            Map.of(
                "maxTokens", 2000,
                "temperature", 0.7,
                "contextWindow", 8000
            ),
            createIdentityConfig("sentrius-keycloak", "service-account-chat", false),
            "Provide helpful, accurate, and conversational assistance to users for general queries, task guidance, and information retrieval.",
            "1. Respond to user queries with accurate and relevant information\n2. Maintain conversation context and coherence\n3. Provide clear and actionable guidance when requested",
            createGuardrails(2000, List.of("no-code-execution", "no-system-access"), 5.0),
            "default-chat-policy",
            createLaunchConfig("1000m", "1Gi", Map.of("LOG_LEVEL", "INFO")),
            1
        );

        // Code Review Agent Template
        createSystemTemplateIfNotExists(
            "Code Review Agent",
            "Automated code review and quality analysis agent",
            "code-review",
            "fa-code-branch",
            "Development",
            Map.of(
                "reviewDepth", "standard",
                "securityChecks", true,
                "styleChecks", true
            ),
            createIdentityConfig("sentrius-keycloak", "service-account-code-review", false),
            "Analyze code changes for quality, security vulnerabilities, best practices adherence, and potential bugs.",
            "1. Identify security vulnerabilities and coding errors\n2. Suggest improvements aligned with best practices\n3. Ensure code style consistency\n4. Detect potential performance issues",
            createGuardrails(4000, List.of("read-only-code-access", "no-destructive-operations"), 10.0),
            "developer-agent-policy",
            createLaunchConfig("2000m", "2Gi", Map.of("REVIEW_DEPTH", "standard")),
            2
        );

        // Security Audit Agent Template
        createSystemTemplateIfNotExists(
            "Security Audit Agent",
            "Security vulnerability scanning and compliance checking",
            "security-audit",
            "fa-shield-alt",
            "Security",
            Map.of(
                "scanDepth", "full",
                "complianceStandards", List.of("OWASP", "CIS"),
                "reportFormat", "detailed"
            ),
            createIdentityConfig("sentrius-keycloak", "service-account-security-audit", true),
            "Perform comprehensive security audits, vulnerability scanning, and compliance verification against industry standards.",
            "1. Scan for security vulnerabilities using industry-standard tools\n2. Verify compliance with OWASP, CIS, and other standards\n3. Generate detailed security reports with remediation guidance\n4. Track and report security posture metrics",
            createGuardrails(8000, List.of("read-only-access", "no-modification", "audit-all-actions"), 15.0),
            "security-agent-policy",
            createLaunchConfig("2000m", "4Gi", Map.of("SCAN_DEPTH", "full", "COMPLIANCE_STANDARDS", "OWASP,CIS")),
            3
        );

        // Monitoring Agent Template
        createSystemTemplateIfNotExists(
            "Monitoring Agent",
            "Real-time system monitoring and alerting",
            "monitoring",
            "fa-chart-line",
            "Operations",
            Map.of(
                "checkInterval", 60,
                "alertThreshold", "medium",
                "metricsRetention", 7
            ),
            createIdentityConfig("sentrius-keycloak", "service-account-monitoring", false),
            "Monitor system health, performance metrics, and trigger alerts based on predefined thresholds and anomaly detection.",
            "1. Continuously monitor system health and performance\n2. Detect anomalies and performance degradation\n3. Generate timely alerts for critical issues\n4. Provide actionable insights for system optimization",
            createGuardrails(1000, List.of("read-metrics-only", "limited-alerting"), 5.0),
            "monitoring-agent-policy",
            createLaunchConfig("1000m", "1Gi", Map.of("CHECK_INTERVAL", "60", "ALERT_THRESHOLD", "medium")),
            4
        );

        // Data Analysis Agent Template
        createSystemTemplateIfNotExists(
            "Data Analysis Agent",
            "Data processing and analytical insights generation",
            "data-analysis",
            "fa-chart-bar",
            "Analytics",
            Map.of(
                "dataSource", "postgres",
                "analysisType", "statistical",
                "outputFormat", "json"
            ),
            createIdentityConfig("sentrius-keycloak", "service-account-data-analysis", false),
            "Analyze data from various sources to generate statistical insights, trends, and actionable recommendations.",
            "1. Extract and process data from configured sources\n2. Perform statistical and trend analysis\n3. Generate visualizations and reports\n4. Provide data-driven recommendations",
            createGuardrails(5000, List.of("read-only-database", "no-pii-exposure", "rate-limited"), 12.0),
            "analytics-agent-policy",
            createLaunchConfig("1500m", "2Gi", Map.of("DATA_SOURCE", "postgres", "ANALYSIS_TYPE", "statistical")),
            5
        );
        
        log.info("Default agent templates initialized successfully");
    }

    private String createIdentityConfig(String issuer, String subjectPrefix, boolean mfaRequired) {
        try {
            Map<String, Object> config = Map.of(
                "issuer", issuer,
                "subjectPrefix", subjectPrefix,
                "mfaRequired", mfaRequired
            );
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize identity config for issuer={}, subjectPrefix={}, mfaRequired={}", 
                issuer, subjectPrefix, mfaRequired, e);
            throw new IllegalStateException("Failed to create identity configuration", e);
        }
    }

    private String createGuardrails(int maxTokensPerRequest, List<String> restrictions, double rateLimitPerMinute) {
        try {
            Map<String, Object> config = Map.of(
                "maxTokensPerRequest", maxTokensPerRequest,
                "restrictions", restrictions,
                "rateLimitPerMinute", rateLimitPerMinute,
                "requireApprovalFor", List.of("destructive-operations", "external-api-calls")
            );
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize guardrails config with maxTokens={}, restrictions={}, rateLimit={}", 
                maxTokensPerRequest, restrictions, rateLimitPerMinute, e);
            throw new IllegalStateException("Failed to create guardrails configuration", e);
        }
    }

    private String createLaunchConfig(String cpuLimit, String memoryLimit, Map<String, String> envVars) {
        try {
            Map<String, Object> config = Map.of(
                "resources", Map.of(
                    "cpuLimit", cpuLimit,
                    "memoryLimit", memoryLimit
                ),
                "environmentVariables", envVars,
                "restartPolicy", "OnFailure"
            );
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("Failed to serialize launch config with cpu={}, memory={}, envVars={}", 
                cpuLimit, memoryLimit, envVars, e);
            throw new IllegalStateException("Failed to create launch configuration", e);
        }
    }

    private void createSystemTemplateIfNotExists(
        String name,
        String description,
        String agentType,
        String icon,
        String category,
        Map<String, Object> config,
        String identity,
        String purpose,
        String goals,
        String guardrails,
        String trustPolicyId,
        String launchConfiguration,
        int displayOrder
    ) {
        if (templateRepository.findByName(name).isEmpty()) {
            try {
                String configJson = objectMapper.writeValueAsString(config);
                AgentTemplate template = AgentTemplate.builder()
                    .name(name)
                    .description(description)
                    .agentType(agentType)
                    .icon(icon)
                    .category(category)
                    .defaultConfiguration(configJson)
                    .identity(identity)
                    .purpose(purpose)
                    .goals(goals)
                    .guardrails(guardrails)
                    .trustPolicyId(trustPolicyId)
                    .launchConfiguration(launchConfiguration)
                    .systemTemplate(true)
                    .enabled(true)
                    .displayOrder(displayOrder)
                    .build();
                templateRepository.save(template);
                log.info("Created system template: {}", name);
            } catch (Exception e) {
                log.error("Failed to create system template: {}", name, e);
            }
        }
    }

    private AgentTemplateDTO toDTO(AgentTemplate template) {
        return AgentTemplateDTO.builder()
            .id(template.getId())
            .name(template.getName())
            .description(template.getDescription())
            .agentType(template.getAgentType())
            .icon(template.getIcon())
            .category(template.getCategory())
            .defaultConfiguration(template.getDefaultConfiguration())
            .identity(template.getIdentity())
            .purpose(template.getPurpose())
            .goals(template.getGoals())
            .guardrails(template.getGuardrails())
            .trustPolicyId(template.getTrustPolicyId())
            .launchConfiguration(template.getLaunchConfiguration())
            .systemTemplate(template.isSystemTemplate())
            .enabled(template.isEnabled())
            .displayOrder(template.getDisplayOrder())
            .createdBy(template.getCreatedBy())
            .createdAt(template.getCreatedAt())
            .updatedAt(template.getUpdatedAt())
            .build();
    }

    private AgentTemplate fromDTO(AgentTemplateDTO dto) {
        return AgentTemplate.builder()
            .id(dto.getId())
            .name(dto.getName())
            .description(dto.getDescription())
            .agentType(dto.getAgentType())
            .icon(dto.getIcon())
            .category(dto.getCategory())
            .defaultConfiguration(dto.getDefaultConfiguration())
            .identity(dto.getIdentity())
            .purpose(dto.getPurpose())
            .goals(dto.getGoals())
            .guardrails(dto.getGuardrails())
            .trustPolicyId(dto.getTrustPolicyId())
            .launchConfiguration(dto.getLaunchConfiguration())
            .systemTemplate(dto.isSystemTemplate())
            .enabled(dto.isEnabled())
            .displayOrder(dto.getDisplayOrder())
            .createdBy(dto.getCreatedBy())
            .build();
    }
}
