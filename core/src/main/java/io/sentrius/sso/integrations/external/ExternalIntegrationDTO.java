package io.sentrius.sso.integrations.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.model.security.IntegrationSecurityToken;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ExternalIntegrationDTO {
    @Builder.Default
    private Long id = 0L;
    private String name;
    private String username;
    private String connectionType;
    private String description;
    private String icon;
    private String baseUrl;
    private String projectKey;
    private String apiToken;

    public ExternalIntegrationDTO(IntegrationSecurityToken token) throws JsonProcessingException {
        this(token, false);
    }

    public ExternalIntegrationDTO(IntegrationSecurityToken token, boolean includeToken) throws JsonProcessingException {
        var dto = JsonUtil.MAPPER.readValue(token.getConnectionInfo(), ExternalIntegrationDTO.class);
        this.id = token.getId();
        this.name = dto.getName();
        this.connectionType = token.getConnectionType();
        this.username = dto.getUsername();
        this.description = dto.getDescription();
        this.icon = dto.getIcon();
        this.baseUrl = dto.getBaseUrl();
        this.projectKey = dto.getProjectKey();
        if (includeToken) {
            this.apiToken = dto.getApiToken();
        }
    }

}
