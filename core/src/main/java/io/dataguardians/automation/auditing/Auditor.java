package io.dataguardians.automation.auditing;

import java.util.ArrayList;
import java.util.List;
import io.dataguardians.automation.auditing.rules.RuleConfiguration;
import io.dataguardians.config.audting.AuditingConfigProvider;

public class Auditor extends BaseAuditor {

  static List<RuleConfiguration> ruleConfigurationList = null;

  public static List<RuleConfiguration> getRuleConfigurationList() throws ClassNotFoundException {
    if (null == ruleConfigurationList) {
      synchronized (Auditor.class) {
        ruleConfigurationList = new ArrayList<>();
        var auditorClass = AuditingConfigProvider.AuditingConfigProviderFactory.getConfigProvider().getAuditorClass();
        var configName = Class.forName(auditorClass).getSimpleName();
        // we don't need more than 10k rules..
        for (int i = 0; i < 10000; i++) {
          String option = configName + ".rule." + Integer.valueOf(i).toString();
          var rule = AuditingConfigProvider.AuditingConfigProviderFactory.getConfigProvider().getRuleConfig(option);
          if (null == rule) {
            break;
          }
          String[] ruleSplit = rule.split(";");
          if (ruleSplit.length == 2) {
            ruleConfigurationList.add(
                RuleConfiguration.builder()
                    .shortName(ruleSplit[1].trim())
                    .longName(ruleSplit[0].trim())
                    .clazz((Class<? extends AuditorRule>) Class.forName(ruleSplit[0].trim()))
                    .build());
          }
        }
        // RuleAlertAuditor.rule.1
      }
    }
    return ruleConfigurationList;
  }

  public Auditor(Long userId, Long sessionId, Long systemId) {
    super(userId, sessionId, systemId);
  }

  @Override
  protected void onPartial() {
    // do nothing

  }
}
