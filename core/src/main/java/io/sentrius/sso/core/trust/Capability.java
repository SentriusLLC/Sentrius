package io.sentrius.sso.core.trust;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias({"endpoint", "endpoints"}) // Accept both
    private List<String> endpoints = new ArrayList<>();
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    @Builder.Default
    private List<String> commands = new ArrayList<>();
    @Builder.Default
    private List<String> activities = new ArrayList<>();
    @Builder.Default
    private List<String> subcommands = new ArrayList<>();

    public String getId() {
        return id;
    }
}