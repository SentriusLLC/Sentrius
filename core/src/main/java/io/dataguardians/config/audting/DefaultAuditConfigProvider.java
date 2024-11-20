package io.dataguardians.config.audting;

import io.dataguardians.automation.auditing.RuleAlertAuditor;

public class DefaultAuditConfigProvider implements AuditingConfigProvider {

    @Override
    public String getAuditorClass() {
        return RuleAlertAuditor.class.getCanonicalName();
    }

    @Override
    public String getRuleConfig(String property) {
        return null;
    }
}
