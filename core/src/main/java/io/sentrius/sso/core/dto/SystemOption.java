package io.sentrius.sso.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class SystemOption {
    String name;
    String value;
    String description;
    @Builder.Default
    Boolean requiresRestart = false;
    String closestType = "";
    String closestPrimitive = "";
    @Builder.Default
    String group = "General";

    public SystemOption(String name, String value, String description) {
        this.name = name;
        this.value = value;
        this.description = description;
    }
}
