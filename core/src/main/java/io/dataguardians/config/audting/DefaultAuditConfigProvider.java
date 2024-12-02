package io.dataguardians.config.audting;

import io.dataguardians.automation.auditing.AccessTokenAuditor;

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
