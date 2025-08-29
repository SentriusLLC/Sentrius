package io.sentrius.sso.core.services.agents;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.sentrius.sso.core.dto.AgentCommunicationDTO;
import io.sentrius.sso.core.dto.AgentDTO;
import io.sentrius.sso.core.dto.AgentHeartbeatDTO;
import io.sentrius.sso.core.dto.UserTypeDTO;
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
import io.sentrius.sso.provenance.ProvenanceEvent;
import io.sentrius.sso.provenance.kafka.ProvenanceKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
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

    final ProvenanceKafkaProducer provenanceKafkaProducer;


    public AgentService(
        AgentCommunicationRepository agentCommunicationRepository, AgentHeartbeatRepository repository, UserService userService, ATPLPolicyService policyService,
        CryptoService cryptoService,
        KeycloakService keycloakService, ProvenanceKafkaProducer provenanceKafkaProducer
    ) {
        this.agentCommunicationRepository = agentCommunicationRepository;
        this.repository = repository;
        this.userService = userService;
        this.policyService = policyService;
        this.cryptoService = cryptoService;
        this.keycloakService = keycloakService;
        this.provenanceKafkaProducer = provenanceKafkaProducer;
    }

    @Transactional
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
     return getAllAgents(encryptId, List.of(), false);
    }

    public List<AgentDTO> getAllAgents(boolean encryptId, List<String> filteredIds, boolean include) {
        return repository.findAll().stream()
            .filter(heartbeat -> {
                var user = userService.getUserByUsername(heartbeat.getAgentName());
                log.info("Agent {}: {}", heartbeat.getAgentId(), user);
                log.info("Excluding {}: {} -- include/exclude {}", heartbeat.getAgentId(), filteredIds, include);
                if (include){
                    log.info("Including {}: {}", heartbeat.getAgentId(), filteredIds);
                    return user != null && filteredIds.contains(user.getUserId());
                }
                else {
                    log.info("Excluding {}: {}", heartbeat.getAgentId(), filteredIds);
                    return user != null && !filteredIds.contains(user.getUserId());
                }
            })
            .map(heartbeat -> {
                log.info("Second Agent {}", heartbeat.getAgentName());
                var user = userService.getUserByUsername(heartbeat.getAgentName());

                var dtoBuilder = AgentDTO.builder();
                if (null != user && user.getAuthorizationType() != UserType.createUnknownUser()){
                    var policy = policyService.getPolicy(user);
                    if (policy.isPresent()) {
                        dtoBuilder.policyId(policy.get().getPolicyId());
                        dtoBuilder.isRegistered(true);
                        dtoBuilder.lastHeartbeat(heartbeat.getLastHeartbeat().toString());
                        dtoBuilder.agentName(heartbeat.getAgentName());
                        var callback = callbackUrls.get(heartbeat.getAgentId());
                        if (callback != null) {

                            dtoBuilder.agentCallback(callback);
                        }
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

            }).collect(Collectors.toUnmodifiableList());
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

        try {
            var eventType = switch(messageType){
                case "intercept" -> ProvenanceEvent.EventType.ENDPOINT_ACCESS;
                case "chat_request" -> ProvenanceEvent.EventType.AGENT_RESPOND;
                case "interpretation_response" -> ProvenanceEvent.EventType.AGENT_RESPOND;
                default -> ProvenanceEvent.EventType.UNKNOWN;
            };
            ProvenanceEvent event = ProvenanceEvent.builder()
                .eventId(communicationId)
                .actor(sourceAgent)
                .triggeringUser(targetAgent)
                .eventType(eventType)
                .outputSummary("Interpretation request sent to OpenAI")
                .timestamp(java.time.Instant.now())
                .sourceDocs(new ArrayList<>()) // no source docs for this
                .build();

            provenanceKafkaProducer.send(event);
        }catch (Exception e){
            log.error("Error saving provenance", e);
        }


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

        try {
            var eventType = switch(communication.getMessageType()){
                case "intercept" -> ProvenanceEvent.EventType.ENDPOINT_ACCESS;
                case "chat_request" -> ProvenanceEvent.EventType.AGENT_RESPOND;
                case "interpretation_response" -> ProvenanceEvent.EventType.INTERPRET_MESSAGE;
                default -> ProvenanceEvent.EventType.UNKNOWN;
            };
            ProvenanceEvent event = ProvenanceEvent.builder()
                .eventId(agentCommunicationDTO.getCommunicationId().toString())
                .actor(agentCommunicationDTO.getSourceAgent())
                .triggeringUser(agentCommunicationDTO.getTargetAgent())
                .eventType(eventType)
                .outputSummary("Interpretation request sent to OpenAI")
                .timestamp(java.time.Instant.now())
                .sourceDocs(new ArrayList<>()) // no source docs for this
                .build();

            provenanceKafkaProducer.send(event);
        }catch (Exception e){
            log.error("Error saving provenance", e);
        }
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
    public CompletableFuture<Void> ping(User user) {
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
        return CompletableFuture.completedFuture(null);
    }

    public boolean isAgent(UserTypeDTO userDto) {
        return true;
    }

    public void setCallBack(User user, String agentCallbackUrl) {
        callbackUrls.put(user.getUserId(), agentCallbackUrl);
    }

    public List<AgentDTO> getAvailableAgents() {
        return getAllAgents(true, callbackUrls.keySet().stream().toList(), true);
    }

    @Scheduled(fixedDelay = 60000) // Runs every 60 seconds
    @Async
    public void pingAndRemoveUnavailableAgents() {
        List<AgentHeartbeat> allAgents = repository.findAll();
        for (AgentHeartbeat heartbeat : allAgents) {
            String agentId = heartbeat.getAgentId();
            try {
                User user = userService.getUserByUsername(agentId);
                if (user == null) {
                    // Remove agent if user not found
                    repository.delete(heartbeat);
                    continue;
                }
                log.info("Ping user {}: {}", user.getUserId(), heartbeat);
                ping(user).join(); // This will update the pingCache
                Optional<AgentStatus> status = getPing(user);
                if (status.isEmpty()) {
                    // Remove agent if not available
                    repository.delete(heartbeat);
                    keycloakService.removeAgentClient(agentId);
                    log.info("Removed unavailable agent: {}", agentId);
                }
            } catch (Exception e) {
                repository.delete(heartbeat);
                log.info("Removed agent due to exception: {}", agentId, e);
            }
        }
    }
}
