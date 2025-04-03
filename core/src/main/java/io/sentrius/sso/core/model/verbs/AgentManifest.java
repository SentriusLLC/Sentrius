package io.sentrius.sso.core.model.verbs;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Builder
@Data
@Getter
@Setter
public class AgentManifest {
    private String agentId;
    private String version;
    @Builder.Default
    private List<VerbDescriptor> supportedVerbs = new ArrayList<>();
}
