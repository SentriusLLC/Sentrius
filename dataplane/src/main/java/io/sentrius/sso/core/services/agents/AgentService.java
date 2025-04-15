package io.sentrius.sso.core.services.agents;

import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import io.sentrius.sso.core.dto.AgentDTO;
import io.sentrius.sso.core.model.AgentHeartbeat;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.repository.AgentCommunicationRepository;
import io.sentrius.sso.core.repository.AgentHeartbeatRepository;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.CryptoService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final AgentCommunicationRepository agentCommunicationRepository;
    private final AgentHeartbeatRepository repository;
    private final UserService userService;
    private final ATPLPolicyService policyService;
    private final CryptoService cryptoService;

    public AgentService(
        AgentCommunicationRepository agentCommunicationRepository, AgentHeartbeatRepository repository, UserService userService, ATPLPolicyService policyService,
        CryptoService cryptoService
    ) {
        this.agentCommunicationRepository = agentCommunicationRepository;
        this.repository = repository;
        this.userService = userService;
        this.policyService = policyService;
        this.cryptoService = cryptoService;
    }

    public void recordHeartbeat(String agentId, String name, String status) {
        AgentHeartbeat heartbeat = repository.findByAgentId(agentId)
            .orElse(new AgentHeartbeat());
        heartbeat.setAgentId(agentId);
        heartbeat.setLastHeartbeat(LocalDateTime.now());
        heartbeat.setAgentName(name);
        heartbeat.setStatus(status);
        repository.save(heartbeat);
    }

    public AgentHeartbeat getHeartbeat(String agentId) {
        return repository.findByAgentId(agentId)
            .orElseThrow(() -> new RuntimeException("Agent not found"));
    }


    public List<AgentDTO> getAllAgents(boolean encryptId) {
        return repository.findAll().stream()
            .map(heartbeat -> {
                var user = userService.getUserWithDetails(heartbeat.getAgentId());

                var dtoBuilder = AgentDTO.builder();
                if (null != user && user.getAuthorizationType() != UserType.createUnknownUser()){
                    var policy = policyService.getPolicy(user);
                    if (policy.isPresent()) {
                        dtoBuilder.policyId(policy.get().getPolicyId());
                        dtoBuilder.isRegistered(true);
                        dtoBuilder.lastHeartbeat(heartbeat.getLastHeartbeat().toString());
                        dtoBuilder.agentName(heartbeat.getAgentName());
                    }
                    if (encryptId){
                        try {
                            dtoBuilder.agentId(cryptoService.encrypt(user.getUsername()));
                        } catch (GeneralSecurityException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        dtoBuilder.agentId(user.getUsername());
                    }
                }

                return dtoBuilder.build();

            })
            .toList();

    }

    @Async
    public CompletableFuture<AgentCommunication> saveCommunication(String sourceAgent, String targetAgent, String messageType, String payload) {
        AgentCommunication communication = AgentCommunication.builder()
            .sourceAgent(sourceAgent)
            .targetAgent(targetAgent)
            .messageType(messageType)
            .payload(payload)
            .build();
        return CompletableFuture.completedFuture(agentCommunicationRepository.save(communication));

    }

    public AgentCommunication getCommunication(Long id) {
        return agentCommunicationRepository.findById(id).orElseThrow(() -> new RuntimeException("Communication not found: " + id));
    }

    public List<AgentCommunication> getCommunications(String sourceAgent) {
        return agentCommunicationRepository.findBySourceAgent(sourceAgent);
    }

    public List<AgentCommunication> getCommunications(String sourceAgent, String targetAgent) {
        return agentCommunicationRepository.findBySourceAgent(sourceAgent);
    }
}
