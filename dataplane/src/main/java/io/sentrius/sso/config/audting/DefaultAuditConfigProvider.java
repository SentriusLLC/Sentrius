package io.sentrius.sso.config.audting;

import io.sentrius.sso.automation.auditing.AccessTokenAuditor;

public class DefaultAuditConfigProvider implements AuditingConfigProvider {

    @Override
    public String getAuditorClass() {
        return AccessTokenAuditor.class.getCanonicalName();
    }

    @Override
    public String getRuleConfig(String property) {
        return null;
    }
}
