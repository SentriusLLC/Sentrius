package io.sentrius.sso.core.dto.agents;

import java.time.Instant;
import java.util.UUID;

public interface AgentContextLineageProjection {
    UUID getId();
    String getName();
    String getDescription();
    Instant getCreatedAt();
    Instant getUpdatedAt();
    Integer getGeneration();
    UUID getParentId();
    String getMemoryNamespace();
    Double getTrustScore();
    String getPolicyId();
}