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
public class VerbDescriptor {
    private String name;
    private String description;
    private Class<?> returnType; // Optional: can be used to specify the expected return type
    @Deprecated
    private List<VerbParam> params = new ArrayList<>();
    private boolean requiresZtat; // Optional: move this to policy if preferred
}
