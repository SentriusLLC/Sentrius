package io.sentrius.sso.core.services.agents;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.Maps;
import io.sentrius.sso.core.dto.AgentCommunicationDTO;
import io.sentrius.sso.core.dto.AgentDTO;
import io.sentrius.sso.core.dto.AgentHeartbeatDTO;
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
import io.sentrius.sso.core.services.security.KeycloakService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class AgentService {

    private final AgentCommunicationRepository agentCommunicationRepository;
    private final AgentHeartbeatRepository repository;
    private final UserService userService;
    private final ATPLPolicyService policyService;
    private final CryptoService cryptoService;

    private ConcurrentMap<String, String> callbackUrls = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();

    private final Cache<String, AgentStatus> pingCache =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    private final KeycloakService keycloakService;


    public AgentService(
        AgentCommunicationRepository agentCommunicationRepository, AgentHeartbeatRepository repository, UserService userService, ATPLPolicyService policyService,
        CryptoService cryptoService,
        KeycloakService keycloakService
    ) {
        this.agentCommunicationRepository = agentCommunicationRepository;
        this.repository = repository;
        this.userService = userService;
        this.policyService = policyService;
        this.cryptoService = cryptoService;
        this.keycloakService = keycloakService;
    }

    public void recordHeartbeat(String agentId, String name, AgentHeartbeatDTO heartbeatDTO) {
        AgentHeartbeat heartbeat = repository.findByAgentId(agentId)
            .orElse(new AgentHeartbeat());
        heartbeat.setAgentId(agentId);
        heartbeat.setLastHeartbeat(LocalDateTime.now());
        heartbeat.setAgentName(name);
        heartbeat.setAgentUrl(heartbeatDTO.getAgentUrl());
        heartbeat.setStatus(heartbeatDTO.getStatus());
        repository.save(heartbeat);
    }

    public AgentHeartbeat getHeartbeat(String agentId) {
        return repository.findByAgentId(agentId)
            .orElseThrow(() -> new RuntimeException("Agent " + agentId + " not found"));
    }


    public List<AgentDTO> getAllAgents(boolean encryptId) {
     return getAllAgents(encryptId, List.of());
    }

    public List<AgentDTO> getAllAgents(boolean encryptId, List<String> filteredIds) {
        return repository.findAll().stream()
            .filter(heartbeat -> {
                var user = userService.getUserByUsername(heartbeat.getAgentId());
                return !filteredIds.contains(user.getUserId());
            })
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


    public CompletableFuture<AgentCommunication> saveCommunication(AgentCommunicationDTO agentCommunicationDTO) {
        AgentCommunication communication = AgentCommunication.builder()
            .sourceAgent(agentCommunicationDTO.getSourceAgent())
            .targetAgent(agentCommunicationDTO.getTargetAgent())
            .messageType(agentCommunicationDTO.getMessageType())
            .communicationId(agentCommunicationDTO.getCommunicationId())
            .payload(agentCommunicationDTO.getPayload())
            .build();
        return CompletableFuture.completedFuture(agentCommunicationRepository.save(communication));

    }

    public AgentCommunication getCommunication(Long id) {
        return agentCommunicationRepository.findById(id).orElseThrow(() -> new RuntimeException("Communication not found: " + id));
    }

    public List<AgentCommunication> getCommunications(UUID communicationId) {
        return agentCommunicationRepository.findByCommunicationId(communicationId);
    }

    public List<AgentCommunication> getCommunicationsTo(UUID communicationId, String targetAgent) {
        return agentCommunicationRepository.findByCommunicationIdAndTargetAgent(communicationId, targetAgent);
    }


    public List<AgentCommunication> getCommunications(String sourceAgent) {
        return agentCommunicationRepository.findBySourceAgent(sourceAgent);
    }

    public List<AgentCommunication> getCommunications(String sourceAgent, String targetAgent) {
        return agentCommunicationRepository.findBySourceAgent(sourceAgent);
    }

    public Page<AgentCommunication> getCommunications(
        String sourceAgent,
        LocalDateTime start,
        LocalDateTime end,
        String type,
        Pageable pageable
    ) {
        Instant startInstant = start.atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = end.atZone(ZoneId.systemDefault()).toInstant();

        if (type != null && !type.isBlank()) {
            return agentCommunicationRepository.findBySourceAgentAndMessageTypeAndCreatedAtBetween(
                sourceAgent, type, startInstant, endInstant, pageable);
        }


        return agentCommunicationRepository.findBySourceAgentAndCreatedAtBetween(
            sourceAgent, startInstant, endInstant, pageable);
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
        log.info("Ping user {}: {}", user.getUserId(), status);
        if (status == null) {
            var heartbeat = getHeartbeat(user.getUserId());
            var url = heartbeat.getAgentUrl();
            if (url == null) {
                throw new RuntimeException("Agent URL not found");
            }

            log.info("Ping URL: {}",  url);
            if (url.startsWith("http")) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                headers.setBearerAuth(keycloakService.getJwtToken()); // <- your JWT or access token
                HttpEntity<Void> request = new HttpEntity<>(headers);
                try {
                    if (url.endsWith("/")) {
                        url = url.substring(0, url.length() - 1);
                    }

                    url = url + "/api/v1/agent/ping";
                    ResponseEntity<AgentStatus> response = restTemplate.exchange(url,
                        HttpMethod.GET, request, AgentStatus.class);
                    if (response.getStatusCode().is2xxSuccessful() && null != response.getBody()) {
                        log.info("Ping successful for URL: {}", url);
                        pingCache.put(user.getUserId(), response.getBody());
                    } else {
                        log.warn("Ping failed for URL: {} with status code: {}", url, response.getStatusCode());
                    }
                } catch (Exception e) {
                    pingCache.invalidate(user.getUserId());
                    log.error("Error while pinging URL: {}", url, e);
                }
            }

        }
    }

    public boolean isAgent(UserTypeDTO userDto) {
        return true;
    }

    public void setCallBack(User user, String agentCallbackUrl) {
        callbackUrls.put(user.getUserId(), agentCallbackUrl);
    }

    public List<AgentDTO> getAvailableAgents() {
        return getAllAgents(false, callbackUrls.keySet().stream().toList());
    }
}
