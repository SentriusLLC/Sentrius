package io.sentrius.sso.core.dto.agents;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTemplateDTO {
    
    private UUID id;
    private String name;
    private String description;
    private String agentType;
    private String icon;
    private String category;
    private String defaultConfiguration;
    private String identity;
    private String purpose;
    private String goals;
    private String guardrails;
    private String trustPolicyId;
    private String launchConfiguration;
    private boolean systemTemplate;
    private boolean enabled;
    private int displayOrder;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
