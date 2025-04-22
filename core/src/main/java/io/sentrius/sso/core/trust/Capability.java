package io.sentrius.sso.core.trust;

import java.util.ArrayList;
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
public class Capability {
    private String id;
    private String description;
    @Builder.Default
    private List<String> endpoint = new ArrayList<>();
    private List<String> tags;

    public String getId() {
        return id;
    }
}