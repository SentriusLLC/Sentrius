package io.sentrius.sso.core.services.agents;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import io.sentrius.sso.core.dto.agents.AgentContextLineageProjection;
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

    public AgentContext getContextOrThrow(String name) {
        return contextRepo.findByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Agent context not found: " + name));
    }

    public List<AgentContext> getLineage(UUID agentId) {
        log.info("Getting lineage for agent: {}", agentId);
        List<AgentContext> lineage = new ArrayList<>();
        AgentContext current = contextRepo.findById(agentId).orElse(null);
        
        if (current == null) {
            return lineage;
        }
        
        // First, traverse up to find the root ancestor
        AgentContext root = current;
        List<AgentContext> ancestors = new ArrayList<>();
        while (root.getParentId() != null) {
            AgentContext parent = contextRepo.findById(root.getParentId()).orElse(null);
            if (parent != null) {
                ancestors.add(0, parent);
                root = parent;
            } else {
                break;
            }
        }
        
        // Add all ancestors to lineage
        lineage.addAll(ancestors);
        
        // Add the current agent if not already in lineage
        if (!lineage.contains(current)) {
            lineage.add(current);
        }
        
        // Now traverse down from current to find all descendants recursively
        addDescendants(current, lineage);
        
        return lineage;
    }
    
    /**
     * Recursively adds all descendants of the given agent to the lineage list.
     */
    private void addDescendants(AgentContext parent, List<AgentContext> lineage) {
        List<AgentContext> children = contextRepo.findByParentId(parent.getId());
        for (AgentContext child : children) {
            if (!lineage.contains(child)) {
                lineage.add(child);
                addDescendants(child, lineage);
            }
        }
    }

    public List<AgentContext> getLineageByName(String agentName) {
        log.info("Getting lineage for agent by name: {}", agentName);
        // Use findLatestByName to handle cases where multiple generations exist with same name
        AgentContext context = contextRepo.findLatestByName(agentName).orElse(null);
        if (context == null) {
            return new ArrayList<>();
        }
        return getLineage(context.getId());
    }


    public List<AgentContextLineageProjection> getLineageProjectionByName(String agentName) {
        log.info("Getting LOB-safe lineage for agent: {}", agentName);

        // 1. Get latest generation (top of lineage)
        var latest = contextRepo.findLatestProjectionByName(agentName).orElse(null);
        if (latest == null) {
            return Collections.emptyList();
        }

        // 2. Traverse UP to root
        List<AgentContextLineageProjection> lineage = new ArrayList<>();
        AgentContextLineageProjection current = latest;

        while (current.getParentId() != null) {
            var parent = contextRepo.findProjectionById(current.getParentId()).orElse(null);
            if (parent == null) break;
            lineage.add(0, parent); // prepend
            current = parent;
        }

        // 3. Add latest (if not already added)
        lineage.add(latest);

        // 4. Traverse DOWN recursively
        addProjectionDescendants(latest, lineage);

        return lineage;
    }

    private void addProjectionDescendants(AgentContextLineageProjection parent,
                                          List<AgentContextLineageProjection> lineage) {

        List<AgentContextLineageProjection> children =
            contextRepo.findProjectionByParentId(parent.getId());

        for (var child : children) {
            if (!lineage.contains(child)) {
                lineage.add(child);
                addProjectionDescendants(child, lineage); // recursion
            }
        }
    }


    public long getInheritedMemoryCount(UUID agentId) {
        // Get agent name from context
        AgentContext context = contextRepo.findById(agentId).orElse(null);
        if (context == null) {
            log.warn("Agent context not found for ID: {}", agentId);
            return 0;
        }
        // Use agent name as agentId for memory queries (consistent with how memories are stored)
        return memoryRepo.countByAgentIdAndMarkingsContaining(context.getName(), "INHERITED");
    }

    /**
     * Get inherited memory count by agent name directly.
     * This is more efficient when the agent name is already available (e.g., from projections).
     * 
     * @param agentName the name of the agent
     * @return count of inherited memories
     */
    public long getInheritedMemoryCount(String agentName) {
        // Use agent name as agentId for memory queries (consistent with how memories are stored)
        return memoryRepo.countByAgentIdAndMarkingsContaining(agentName, "INHERITED");
    }
    
    /**
     * Gets or creates an agent context for the given agent name.
     * This ensures every agent has a context for generation purposes.
     * Returns the latest generation if multiple contexts exist for the same name.
     */
    @Transactional
    public AgentContext getOrCreateContext(String agentName) {
        log.debug("Getting or creating agent context for: {}", agentName);
        // Use findLatestByName to get the most recent generation
        return contextRepo.findLatestByName(agentName)
            .orElseGet(() -> {
                log.info("Creating default agent context for: {}", agentName);
                AgentContext context = new AgentContext();
                context.setName(agentName);
                context.setDescription("Auto-created context for agent: " + agentName);
                context.setContext(""); // Empty context, can be populated later
                context.setGeneration(1);
                context.setTrustScore(0.5);
                context.setMemoryNamespace("agents/" + agentName + "_v1");
                return contextRepo.save(context);
            });
    }

    public void updateAgentNameByGenerationId(UUID generationId, String username) {
        log.info("Updating agent name for generation ID: {} to {}", generationId, username);
        AgentContext context = contextRepo.findById(generationId)
            .orElseThrow(() -> new IllegalArgumentException("Agent context not found: " + generationId));
        context.setName(username);
        contextRepo.save(context);
    }
}