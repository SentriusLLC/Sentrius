package io.sentrius.sso.install.configuration.dtos;

import java.util.List;
import io.sentrius.sso.core.model.hostgroup.ProfileConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleDTO {
    private String displayName;
    private String description;
    private String ruleClass;
    private String configuration;
}