package io.sentrius.sso.core.services.agents;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import io.sentrius.sso.core.dto.AgentCommunicationDTO;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.repository.AgentCommunicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Service for searching agent memory/communications with various filters
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AgentMemorySearchService {

    private final AgentCommunicationRepository agentCommunicationRepository;

    /**
     * Search agent memories by text content
     */
    public Page<AgentCommunicationDTO> searchByContent(String searchTerm, int page, int size) {
        log.info("Searching agent memories by content: {}", searchTerm);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AgentCommunication> results = agentCommunicationRepository
            .findByPayloadContainingIgnoreCase(searchTerm, pageable);
            
        return results.map(AgentCommunication::toDTO);
    }

    /**
     * Search agent memories by agent name (source or target)
     */
    public Page<AgentCommunicationDTO> searchByAgent(String agentName, int page, int size) {
        log.info("Searching agent memories by agent: {}", agentName);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AgentCommunication> results = agentCommunicationRepository
            .findBySourceAgentContainingIgnoreCaseOrTargetAgentContainingIgnoreCase(agentName, agentName, pageable);
            
        return results.map(AgentCommunication::toDTO);
    }

    /**
     * Search agent memories by content and date range
     */
    public Page<AgentCommunicationDTO> searchByContentAndDateRange(
        String searchTerm, 
        LocalDateTime startDate, 
        LocalDateTime endDate, 
        int page, 
        int size
    ) {
        log.info("Searching agent memories by content '{}' between {} and {}", searchTerm, startDate, endDate);
        
        Instant start = startDate.atZone(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.atZone(ZoneId.systemDefault()).toInstant();
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AgentCommunication> results = agentCommunicationRepository
            .findByPayloadContainingIgnoreCaseAndCreatedAtBetween(searchTerm, start, end, pageable);
            
        return results.map(AgentCommunication::toDTO);
    }

    /**
     * Search agent memories by specific agent and content
     */
    public Page<AgentCommunicationDTO> searchByAgentAndContent(
        String agentName, 
        String searchTerm, 
        int page, 
        int size
    ) {
        log.info("Searching agent memories for agent '{}' with content '{}'", agentName, searchTerm);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AgentCommunication> results = agentCommunicationRepository
            .findBySourceAgentAndPayloadContainingIgnoreCase(agentName, searchTerm, pageable);
            
        return results.map(AgentCommunication::toDTO);
    }

    /**
     * Get all agent memories with pagination
     */
    public Page<AgentCommunicationDTO> getAllMemories(int page, int size) {
        log.info("Retrieving all agent memories with pagination");
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AgentCommunication> results = agentCommunicationRepository.findAll(pageable);
        
        return results.map(AgentCommunication::toDTO);
    }
}