package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.agents.AgentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentTemplateRepository extends JpaRepository<AgentTemplate, UUID> {
    
    /**
     * Find all enabled templates
     */
    List<AgentTemplate> findByEnabledTrueOrderByDisplayOrderAsc();
    
    /**
     * Find templates by category
     */
    List<AgentTemplate> findByCategoryAndEnabledTrueOrderByDisplayOrderAsc(String category);
    
    /**
     * Find template by name
     */
    Optional<AgentTemplate> findByName(String name);
    
    /**
     * Find all system templates
     */
    List<AgentTemplate> findBySystemTemplateTrueOrderByDisplayOrderAsc();
}
