package io.dataguardians.sso.core.utils;

import java.util.ArrayList;
import java.util.List;
import io.dataguardians.automation.auditing.AuditorRule;
import io.dataguardians.automation.auditing.RuleAlertAuditor;
import io.dataguardians.automation.auditing.rules.RuleConfiguration;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.config.ThreadSafeDynamicPropertiesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditConfigProvider {


    private final SystemOptions systemOptions;
    private final ThreadSafeDynamicPropertiesService dynamicPropertiesService;



    static List<RuleConfiguration> ruleConfigurationList = null;

    public static List<RuleConfiguration> getRuleConfigurationList(ThreadSafeDynamicPropertiesService dynamicPropertiesService) throws ClassNotFoundException {
        if (null == ruleConfigurationList) {
            synchronized (AuditConfigProvider.class) {
                ruleConfigurationList = new ArrayList<>();
                var auditorClass = RuleAlertAuditor.class.getName();
                var configName = Class.forName(auditorClass).getSimpleName();
                // we don't need more than 10k rules..
                for (int i = 0; i < 10000; i++) {
                    String option = configName + ".rule." + Integer.valueOf(i).toString();
                    var rule = dynamicPropertiesService.getProperty(option, null);
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

    public List<RuleConfiguration> getRuleConfigurationList() throws ClassNotFoundException {
        return getRuleConfigurationList(dynamicPropertiesService);
    }
}
