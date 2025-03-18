package io.sentrius.sso.install.configuration.dtos;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HostGroupConfigurationDTO {
    private String displayName;
    private String description;
    private List<String> systems;
    @Builder.Default
    private List<String> assignedRules = new ArrayList<>();
    private ProfileConfigurationDTO configuration;
}
