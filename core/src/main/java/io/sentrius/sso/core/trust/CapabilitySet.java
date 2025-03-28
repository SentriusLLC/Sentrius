package io.sentrius.sso.core.trust;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Data
@Builder
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CapabilitySet {
    private List<Capability> primitives;
    private List<ComposedCapability> composed;

    public Set<String> resolve(AgentContext ctx) {
        Set<String> resolved = new HashSet<>();
        for (Capability cap : primitives) {
            resolved.add(cap.getId());
        }
        for (ComposedCapability comp : composed) {
            resolved.add(comp.getId());
            resolved.addAll(comp.getIncludes());
        }
        return resolved;
    }
}