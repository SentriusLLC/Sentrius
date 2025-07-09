package io.sentrius.sso.core.services.agents;


import java.util.UUID;
import io.sentrius.sso.core.dto.agents.AgentContextRequestDTO;
import io.sentrius.sso.core.model.agents.AgentContext;
import io.sentrius.sso.core.repository.AgentContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AgentContextService {

    private final AgentContextRepository contextRepo;

    public AgentContextService(AgentContextRepository contextRepo) {
        this.contextRepo = contextRepo;
    }

    public AgentContext create(AgentContextRequestDTO dto) {
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
}