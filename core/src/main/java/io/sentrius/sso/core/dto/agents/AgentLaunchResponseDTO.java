package io.sentrius.sso.core.dto.agents;
import java.util.UUID;

public class AgentLaunchResponseDTO {
    private String agentId;
    private UUID contextId;
    private String launchedBy;
    private String launchParameters; // Optional

    // Getters and setters
}
