package io.sentrius.sso.controllers.view;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.stream.Collectors;
import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.agents.AgentContextDTO;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.AgentContextService;
import io.sentrius.sso.core.services.auditing.AuditService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/sso/v1/agent")
public class AgentController extends BaseController {
    private final AuditService auditService;
    private final CryptoService cryptoService;
    private final SessionTrackingService sessionTrackingService;
    private final AgentContextService agentContextService;

    public AgentController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        AuditService auditService,
        CryptoService cryptoService,
        SessionTrackingService sessionTrackingService,
        AgentContextService agentContextService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.auditService = auditService;
        this.cryptoService = cryptoService;
        this.sessionTrackingService = sessionTrackingService;
        this.agentContextService = agentContextService;
    }

    @GetMapping("/list")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String listAgents(Model m) {
        return "sso/agents/list_agents";
    }

    @GetMapping("/design/chat")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String designAgent(Model m) {
        return "sso/agents/design_chat";
    }

    @GetMapping("/connections")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String listConnections(Model m, @RequestParam("agentId") String agentId) throws GeneralSecurityException {
        var aid = URLDecoder.decode(agentId, StandardCharsets.UTF_8);
        log.info("Received policy request from agent: {} {} ",aid, agentId);
        var decrypted = cryptoService.decrypt(aid);
        m.addAttribute("agentId",agentId);
        m.addAttribute("callTypes", List.of("intercept","chat_request","atat_chat_respond", "atat_chat_ask"));
        return "sso/agents/agent_comms";
    }

    @GetMapping("/memory/search")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public String searchAgentMemory(Model m) {
        return "sso/agents/memory_search";
    }

    @GetMapping("/context/{agentName}/lineage")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<List<AgentContextDTO>> getContextLineageByName(@PathVariable("agentName") String agentName) {
        log.info("Getting lineage for agent by name: {}", agentName);
        var lineage = agentContextService.getLineageByName(agentName);
        
        if (lineage.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        List<AgentContextDTO> lineageDTOs = lineage.stream()
            .map(context -> {
                long inheritedCount = agentContextService.getInheritedMemoryCount(context.getName(), context.getMemoryNamespace());
                return AgentContextDTO.builder()
                    .contextId(context.getId())
                    .name(context.getName())
                    .description(context.getDescription())
                    .context(context.getContext())
                    .createdAt(context.getCreatedAt())
                    .updatedAt(context.getUpdatedAt())
                    .generation(context.getGeneration())
                    .parentId(context.getParentId())
                    .memoryNamespace(context.getMemoryNamespace())
                    .trustScore(context.getTrustScore())
                    .policyId(context.getPolicyId())
                    .inheritedMemoryCount(inheritedCount)
                    .build();
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(lineageDTOs);
    }

}
