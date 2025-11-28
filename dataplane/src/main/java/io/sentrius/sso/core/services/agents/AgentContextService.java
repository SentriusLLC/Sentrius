package io.sentrius.sso.core.services.agents;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import io.sentrius.sso.core.dto.agents.AgentContextLineageProjection;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.agents.AgentContextRequestDTO;
import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.promptadvisor.service.PromptAdvisorService;
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
    private final PromptAdvisorService promptAdvisorService;
    private final SystemOptions systemOptions;

    public AgentContextService(AgentContextRepository contextRepo,
                                AgentMemoryRepository memoryRepo,
                               PromptAdvisorService promptAdvisorService,
                               SystemOptions systemOptions) {
        this.contextRepo = contextRepo;
            this.memoryRepo = memoryRepo;
        this.promptAdvisorService = promptAdvisorService;
        this.systemOptions = systemOptions;
    }

    @Transactional
    public AgentContext create(@NonNull AgentContextRequestDTO dto) {
        log.info("Creating AgentContext from {}", dto);
        
        // Refine the agent prompt/context using the prompt advisor
        String originalContext = dto.getContext();
        String refinedContext = originalContext;
        
        if (systemOptions.getEnablePromptAdvisor() && originalContext != null && !originalContext.isEmpty()) {
            log.info("Refining agent context/prompt using prompt advisor");
            var context = new HashMap<String, Object>();
            context.put("agent_name", dto.getName());
            context.put("description", dto.getDescription());
            
            refinedContext = promptAdvisorService.refinePrompt(originalContext, context);
            
            if (!refinedContext.equals(originalContext)) {
                log.info("Agent context refined by prompt advisor. Original length: {}, Refined length: {}", 
                    originalContext.length(), refinedContext.length());
            } else {
                log.info("Agent context unchanged by prompt advisor");
            }
        } else {
            log.debug("Prompt advisor disabled or context is empty, using original context");
        }
        
        AgentContext context = new AgentContext();
        context.setName(dto.getName());
        context.setDescription(dto.getDescription());
        context.setContext(refinedContext);
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
        // Use agent name as agentId and memoryNamespace as conversationId for generation-specific counting
        return memoryRepo.countByAgentIdAndMarkingsContainingAndConversationId(
                context.getName(), "INHERITED", context.getMemoryNamespace());
    }

    /**
     * Get inherited memory count by agent name directly.
     * This is more efficient when the agent name is already available (e.g., from projections).
     * 
     * @param agentName the name of the agent
     * @return count of inherited memories for the latest generation
     */
    public long getInheritedMemoryCount(String agentName) {
        // Get the agent context to retrieve the memoryNamespace
        AgentContext context = contextRepo.findLatestByName(agentName).orElse(null);
        if (context == null) {
            log.warn("Agent context not found for name: {}", agentName);
            return 0;
        }
        // Use agent name as agentId and memoryNamespace as conversationId for generation-specific counting
        return memoryRepo.countByAgentIdAndMarkingsContainingAndConversationId(
                agentName, "INHERITED", context.getMemoryNamespace());
    }
    
    /**
     * Get inherited memory count by agent name and memory namespace.
     * This provides precise counting for a specific generation.
     * 
     * @param agentName the name of the agent
     * @param memoryNamespace the memory namespace for the specific generation
     * @return count of inherited memories for that specific generation
     */
    public long getInheritedMemoryCount(String agentName, String memoryNamespace) {
        // Use agent name as agentId and memoryNamespace as conversationId for generation-specific counting
        return memoryRepo.countByAgentIdAndMarkingsContainingAndConversationId(
                agentName, "INHERITED", memoryNamespace);
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