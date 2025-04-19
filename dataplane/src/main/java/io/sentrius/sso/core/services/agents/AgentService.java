package io.sentrius.sso.core.services.agents;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.sentrius.sso.core.dto.AgentDTO;
import io.sentrius.sso.core.dto.UserDTO;
import io.sentrius.sso.core.dto.UserTypeDTO;
import io.sentrius.sso.core.dto.ztat.AgentExecution;
import io.sentrius.sso.core.model.AgentHeartbeat;
import io.sentrius.sso.core.model.AgentStatus;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.AgentCommunicationRepository;
import io.sentrius.sso.core.repository.AgentHeartbeatRepository;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.CryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class AgentService {

    private final AgentCommunicationRepository agentCommunicationRepository;
    private final AgentHeartbeatRepository repository;
    private final UserService userService;
    private final ATPLPolicyService policyService;
    private final CryptoService cryptoService;

    private final Cache<String, AgentStatus> pingCache =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();


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
                var user = userService.getUserByUsername(heartbeat.getAgentId());

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
                            // this is obfuscation of something known, let's use a real id of some kind
                            dtoBuilder.agentId(cryptoService.encrypt(user.getUserId()));
                        } catch (GeneralSecurityException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        dtoBuilder.agentId(user.getUserId());
                    }
                }

                return dtoBuilder.build();

            })
            .toList();

    }

    @Async
    public CompletableFuture<AgentCommunication> saveCommunication(String communicationId, String sourceAgent,
                                                                   String targetAgent, String messageType, String payload) {
        AgentCommunication communication = AgentCommunication.builder()
            .sourceAgent(sourceAgent)
            .targetAgent(targetAgent)
            .messageType(messageType)
            .communicationId(UUID.fromString(communicationId))
            .payload(payload)
            .build();
        return CompletableFuture.completedFuture(agentCommunicationRepository.save(communication));

    }

    public AgentCommunication getCommunication(Long id) {
        return agentCommunicationRepository.findById(id).orElseThrow(() -> new RuntimeException("Communication not found: " + id));
    }

    public List<AgentCommunication> getCommunications(UUID communicationId) {
        return agentCommunicationRepository.findBySourceAgent(communicationId.toString());
    }


    public List<AgentCommunication> getCommunications(String sourceAgent) {
        return agentCommunicationRepository.findBySourceAgent(sourceAgent);
    }

    public List<AgentCommunication> getCommunications(String sourceAgent, String targetAgent) {
        return agentCommunicationRepository.findBySourceAgent(sourceAgent);
    }

    public Page<AgentCommunication> getCommunications(String sourceAgent, LocalDateTime start, LocalDateTime end,
                                                      Pageable pageable) {
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = end.atZone(ZoneId.systemDefault()).toInstant();
        return agentCommunicationRepository.findBySourceAgentAndCreatedAtBetween(sourceAgent, startInstant, endInstant, pageable);
    }

    public Optional<AgentStatus> getPing(User user) {
        return Optional.ofNullable(pingCache.getIfPresent(user.getUserId()));
    }

    public void setPing(User user, AgentStatus status) {
        pingCache.put(user.getUserId(), status);
    }

    @Async
    public void ping(User user) {
        AgentStatus status = pingCache.getIfPresent(user.getUserId());
        if (status == null) {
            var heartbeat = getHeartbeat(user.getUserId());
            var url = heartbeat.getAgentUrl();
            if (url == null) {
                throw new RuntimeException("Agent URL not found");
            }


            if (url.startsWith("http")) {
                RestTemplate restTemplate = new RestTemplate();
                try {
                    ResponseEntity<AgentStatus> response = restTemplate.getForEntity(url, AgentStatus.class);
                    if (response.getStatusCode().is2xxSuccessful() && null != response.getBody()) {
                        log.info("Ping successful for URL: {}", url);
                        pingCache.put(user.getUserId(), response.getBody());
                    } else {
                        log.warn("Ping failed for URL: {} with status code: {}", url, response.getStatusCode());
                    }
                } catch (Exception e) {
                    log.error("Error while pinging URL: {}", url, e);
                }
            }

        }
    }

    public boolean isAgent(UserTypeDTO userDto) {
        return true;
    }
}
