package io.sentrius.sso.core.model.users;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserConfig {

    @Builder.Default
    private Boolean terminalsInNewTab = true;

    @Builder.Default
    private String jiraEmailAddress = "";
}
