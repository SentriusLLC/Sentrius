package io.sentrius.sso.core.trust;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Data
@SuperBuilder
@Setter
@AllArgsConstructor
@NoArgsConstructor
class ComposedCapability extends Capability {
    private List<String> includes;

    public List<String> getIncludes() {
        return includes;
    }
}