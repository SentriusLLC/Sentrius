package io.sentrius.sso.core.dto.agents;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor

@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentContextDTO {
    @Builder.Default
    private UUID contextId = UUID.randomUUID();
    private String name;
    @Builder.Default
    private String description = "";
    private String context; // The YAML or text body
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt =  Instant.now();
}

