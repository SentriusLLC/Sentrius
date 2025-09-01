package io.sentrius.sso.core.services.agents;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import io.sentrius.sso.core.dto.AgentCommunicationDTO;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.repository.AgentCommunicationRepository;

class AgentMemorySearchServiceTest {

    @Mock
    private AgentCommunicationRepository agentCommunicationRepository;

    private AgentMemorySearchService agentMemorySearchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentMemorySearchService = new AgentMemorySearchService(agentCommunicationRepository);
    }

    @Test
    void searchByContent_ShouldReturnResults() {
        // Given
        String searchTerm = "test content";
        int page = 0;
        int size = 20;
        
        AgentCommunication mockComm = createMockAgentCommunication();
        Page<AgentCommunication> mockPage = new PageImpl<>(Arrays.asList(mockComm));
        
        when(agentCommunicationRepository.findByPayloadContainingIgnoreCase(
            eq(searchTerm), any(Pageable.class))).thenReturn(mockPage);

        // When
        Page<AgentCommunicationDTO> result = agentMemorySearchService.searchByContent(searchTerm, page, size);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(mockComm.getSourceAgent(), result.getContent().get(0).getSourceAgent());
        verify(agentCommunicationRepository).findByPayloadContainingIgnoreCase(eq(searchTerm), any(Pageable.class));
    }

    @Test
    void searchByAgent_ShouldReturnResults() {
        // Given
        String agentName = "test-agent";
        int page = 0;
        int size = 20;
        
        AgentCommunication mockComm = createMockAgentCommunication();
        Page<AgentCommunication> mockPage = new PageImpl<>(Arrays.asList(mockComm));
        
        when(agentCommunicationRepository.findBySourceAgentContainingIgnoreCaseOrTargetAgentContainingIgnoreCase(
            eq(agentName), eq(agentName), any(Pageable.class))).thenReturn(mockPage);

        // When
        Page<AgentCommunicationDTO> result = agentMemorySearchService.searchByAgent(agentName, page, size);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(mockComm.getTargetAgent(), result.getContent().get(0).getTargetAgent());
        verify(agentCommunicationRepository).findBySourceAgentContainingIgnoreCaseOrTargetAgentContainingIgnoreCase(
            eq(agentName), eq(agentName), any(Pageable.class));
    }

    @Test
    void searchByAgentAndContent_ShouldReturnResults() {
        // Given
        String agentName = "test-agent";
        String searchTerm = "test content";
        int page = 0;
        int size = 20;
        
        AgentCommunication mockComm = createMockAgentCommunication();
        Page<AgentCommunication> mockPage = new PageImpl<>(Arrays.asList(mockComm));
        
        when(agentCommunicationRepository.findBySourceAgentAndPayloadContainingIgnoreCase(
            eq(agentName), eq(searchTerm), any(Pageable.class))).thenReturn(mockPage);

        // When
        Page<AgentCommunicationDTO> result = agentMemorySearchService.searchByAgentAndContent(agentName, searchTerm, page, size);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(agentCommunicationRepository).findBySourceAgentAndPayloadContainingIgnoreCase(
            eq(agentName), eq(searchTerm), any(Pageable.class));
    }

    @Test
    void getAllMemories_ShouldReturnAllResults() {
        // Given
        int page = 0;
        int size = 20;
        
        AgentCommunication mockComm = createMockAgentCommunication();
        Page<AgentCommunication> mockPage = new PageImpl<>(Arrays.asList(mockComm));
        
        when(agentCommunicationRepository.findAll(any(Pageable.class))).thenReturn(mockPage);

        // When
        Page<AgentCommunicationDTO> result = agentMemorySearchService.getAllMemories(page, size);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(agentCommunicationRepository).findAll(any(Pageable.class));
    }

    private AgentCommunication createMockAgentCommunication() {
        return AgentCommunication.builder()
            .id(1L)
            .sourceAgent("test-source-agent")
            .targetAgent("test-target-agent")
            .messageType("chat_request")
            .communicationId(UUID.randomUUID())
            .payload("test payload content")
            .createdAt(Instant.now())
            .linkedRequests(Arrays.asList()) // Empty list for simplicity
            .build();
    }
}