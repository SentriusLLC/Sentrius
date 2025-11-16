package io.sentrius.sso.core.services.agents;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.sentrius.sso.core.dto.agents.AgentContextRequestDTO;
import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.repository.AgentContextRepository;
import io.sentrius.sso.core.repository.AgentMemoryRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AgentContextService {

    private final AgentContextRepository contextRepo;
    private final AgentMemoryRepository memoryRepo;

    public AgentContextService(AgentContextRepository contextRepo, AgentMemoryRepository memoryRepo) {
        this.contextRepo = contextRepo;
        this.memoryRepo = memoryRepo;
    }

    @Transactional
    public AgentContext create(@NonNull AgentContextRequestDTO dto) {
        log.info("Creating AgentContext from {}", dto);
        AgentContext context = new AgentContext();
        context.setName(dto.getName());
        context.setDescription(dto.getDescription());
        context.setContext(dto.getContext());
        return contextRepo.save(context);
    }

    public AgentContext getContextOrThrow(UUID id) {
        return contextRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Agent context not found: " + id));
    }

    public List<AgentContext> getLineage(UUID agentId) {
        log.info("Getting lineage for agent: {}", agentId);
        List<AgentContext> lineage = new ArrayList<>();
        AgentContext current = contextRepo.findById(agentId).orElse(null);
        
        while (current != null) {
            lineage.add(0, current);
            if (current.getParentId() != null) {
                current = contextRepo.findById(current.getParentId()).orElse(null);
            } else {
                current = null;
            }
        }
        
        return lineage;
    }

    public List<AgentContext> getLineageByName(String agentName) {
        log.info("Getting lineage for agent by name: {}", agentName);
        AgentContext context = contextRepo.findByName(agentName).orElse(null);
        if (context == null) {
            return new ArrayList<>();
        }
        return getLineage(context.getId());
    }

    public long getInheritedMemoryCount(UUID agentId) {
        String agentIdStr = agentId.toString();
        return memoryRepo.countByAgentIdAndMarkingsContaining(agentIdStr, "INHERITED");
    }
}