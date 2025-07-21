package io.sentrius.sso.core.dto.capabilities;

import io.sentrius.sso.core.data.EndpointThreat;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.model.security.enums.IdentityType;
import io.sentrius.sso.core.model.security.enums.RuleAccessEnum;
import io.sentrius.sso.core.model.security.enums.SSHAccessEnum;
import io.sentrius.sso.core.model.security.enums.SystemOperationsEnum;
import io.sentrius.sso.core.model.security.enums.UserAccessEnum;
import io.sentrius.sso.core.model.security.enums.ZeroTrustAccessTokenEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents access limitations extracted from @LimitAccess annotation.
 */
@Builder
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccessLimitations {
    private String notificationMessage;
    private IdentityType[] allowedIdentityTypes;
    private UserAccessEnum[] userAccess;
    private ApplicationAccessEnum[] applicationAccess;
    private RuleAccessEnum[] ruleAccess;
    private SSHAccessEnum[] sshAccess;
    private SystemOperationsEnum[] systemOperations;
    private ZeroTrustAccessTokenEnum[] ztatAccess;
    private EndpointThreat endpointThreat;
    
    @Builder.Default
    private boolean hasLimitAccess = false;
}