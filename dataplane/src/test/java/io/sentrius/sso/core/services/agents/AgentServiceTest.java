package io.sentrius.sso.core.services.agents;

import io.sentrius.sso.core.dto.AgentDTO;
import io.sentrius.sso.core.model.AgentHeartbeat;
import io.sentrius.sso.core.model.security.UserType;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.AgentCommunicationRepository;
import io.sentrius.sso.core.repository.AgentHeartbeatRepository;
import io.sentrius.sso.core.services.ATPLPolicyService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.core.services.security.KeycloakService;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.provenance.kafka.ProvenanceKafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentCommunicationRepository agentCommunicationRepository;

    @Mock
    private AgentHeartbeatRepository repository;

    @Mock
    private UserService userService;

    @Mock
    private ATPLPolicyService policyService;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private ProvenanceKafkaProducer provenanceKafkaProducer;

    @InjectMocks
    private AgentService agentService;

    private User testUser;
    private AgentHeartbeat testHeartbeat;
    private ATPLPolicy testPolicy;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = User.builder()
            .userId("test-user-id")
            .username("test-agent")
            .authorizationType(UserType.createUnknownUser())
            .build();

        // Setup test heartbeat with recent timestamp
        testHeartbeat = new AgentHeartbeat();
        testHeartbeat.setId(1L);
        testHeartbeat.setAgentId("test-user-id");
        testHeartbeat.setAgentName("test-agent");
        testHeartbeat.setLastHeartbeat(LocalDateTime.now().minusMinutes(2));
        testHeartbeat.setStatus("active");
        testHeartbeat.setAgentUrl("http://test-agent:8080");

        // Setup test policy
        testPolicy = ATPLPolicy.builder()
            .policyId("test-policy")
            .build();
    }

    @Test
    void testGetAvailableAgents_WithRecentHeartbeat() throws Exception {
        // Given: An agent with a recent heartbeat
        when(repository.findByLastHeartbeatAfter(any(LocalDateTime.class)))
            .thenReturn(List.of(testHeartbeat));
        when(repository.findAll()).thenReturn(List.of(testHeartbeat));
        when(userService.getUserByUsername("test-agent")).thenReturn(testUser);
        when(policyService.getPolicy(testUser)).thenReturn(Optional.of(testPolicy));
        when(cryptoService.encrypt("test-user-id")).thenReturn("encrypted-test-user-id");

        // When: Getting available agents
        List<AgentDTO> availableAgents = agentService.getAvailableAgents();

        // Then: The agent should be in the list
        assertNotNull(availableAgents);
        assertEquals(1, availableAgents.size());
        
        AgentDTO agent = availableAgents.get(0);
        assertEquals("test-agent", agent.getAgentName());
        assertEquals("encrypted-test-user-id", agent.getAgentId());
        assertEquals("test-policy", agent.getPolicyId());
        assertTrue(agent.isRegistered());
        assertNotNull(agent.getLastHeartbeat());
        
        // Verify the repository was called with a time approximately 5 minutes ago
        verify(repository).findByLastHeartbeatAfter(argThat(time -> 
            time.isAfter(LocalDateTime.now().minusMinutes(6)) && 
            time.isBefore(LocalDateTime.now().minusMinutes(4))
        ));
    }

    @Test
    void testGetAvailableAgents_NoRecentHeartbeats() {
        // Given: No agents with recent heartbeats
        when(repository.findByLastHeartbeatAfter(any(LocalDateTime.class)))
            .thenReturn(List.of());
        when(repository.findAll()).thenReturn(List.of());

        // When: Getting available agents
        List<AgentDTO> availableAgents = agentService.getAvailableAgents();

        // Then: The list should be empty
        assertNotNull(availableAgents);
        assertEquals(0, availableAgents.size());
    }

    @Test
    void testGetAvailableAgents_WithOldHeartbeat() {
        // Given: An agent with an old heartbeat (more than 5 minutes ago)
        testHeartbeat.setLastHeartbeat(LocalDateTime.now().minusMinutes(10));
        when(repository.findByLastHeartbeatAfter(any(LocalDateTime.class)))
            .thenReturn(List.of());
        when(repository.findAll()).thenReturn(List.of());

        // When: Getting available agents
        List<AgentDTO> availableAgents = agentService.getAvailableAgents();

        // Then: The agent should not be in the list
        assertNotNull(availableAgents);
        assertEquals(0, availableAgents.size());
    }

    @Test
    void testGetAvailableAgents_MultipleAgents() throws Exception {
        // Given: Multiple agents with recent heartbeats
        AgentHeartbeat secondHeartbeat = new AgentHeartbeat();
        secondHeartbeat.setId(2L);
        secondHeartbeat.setAgentId("test-user-id-2");
        secondHeartbeat.setAgentName("monitoring-agent");
        secondHeartbeat.setLastHeartbeat(LocalDateTime.now().minusMinutes(1));
        secondHeartbeat.setStatus("active");

        User secondUser = User.builder()
            .userId("test-user-id-2")
            .username("monitoring-agent")
            .authorizationType(UserType.createUnknownUser())
            .build();

        when(repository.findByLastHeartbeatAfter(any(LocalDateTime.class)))
            .thenReturn(List.of(testHeartbeat, secondHeartbeat));
        when(repository.findAll()).thenReturn(List.of(testHeartbeat, secondHeartbeat));
        when(userService.getUserByUsername("test-agent")).thenReturn(testUser);
        when(userService.getUserByUsername("monitoring-agent")).thenReturn(secondUser);
        when(policyService.getPolicy(testUser)).thenReturn(Optional.of(testPolicy));
        when(policyService.getPolicy(secondUser)).thenReturn(Optional.of(testPolicy));
        when(cryptoService.encrypt("test-user-id")).thenReturn("encrypted-test-user-id");
        when(cryptoService.encrypt("test-user-id-2")).thenReturn("encrypted-test-user-id-2");

        // When: Getting available agents
        List<AgentDTO> availableAgents = agentService.getAvailableAgents();

        // Then: Both agents should be in the list
        assertNotNull(availableAgents);
        assertEquals(2, availableAgents.size());
        
        List<String> agentNames = availableAgents.stream()
            .map(AgentDTO::getAgentName)
            .toList();
        assertTrue(agentNames.contains("test-agent"));
        assertTrue(agentNames.contains("monitoring-agent"));
    }

    @Test
    void testRecordHeartbeat() {
        // Given: A new agent heartbeat
        io.sentrius.sso.core.dto.AgentHeartbeatDTO heartbeatDTO = 
            io.sentrius.sso.core.dto.AgentHeartbeatDTO.builder()
                .agentUrl("http://test-agent:8080")
                .status("active")
                .build();
        
        when(repository.findByAgentId("test-user-id")).thenReturn(Optional.empty());
        when(repository.save(any(AgentHeartbeat.class))).thenReturn(testHeartbeat);

        // When: Recording a heartbeat
        agentService.recordHeartbeat("test-user-id", "test-agent", heartbeatDTO);

        // Then: The heartbeat should be saved
        verify(repository).save(argThat(heartbeat -> 
            heartbeat.getAgentId().equals("test-user-id") &&
            heartbeat.getAgentName().equals("test-agent") &&
            heartbeat.getStatus().equals("active") &&
            heartbeat.getAgentUrl().equals("http://test-agent:8080")
        ));
    }
}
