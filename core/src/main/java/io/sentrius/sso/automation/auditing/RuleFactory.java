package io.sentrius.sso.automation.auditing;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.auditing.Rule;
import io.sentrius.sso.core.services.PluggableServices;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.install.configuration.dtos.RuleDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuleFactory {

    public static void createRules(
        SystemOptions systemOptions,
        ConnectedSystem connectedSystem,
        SessionTrackingService sessionTrackingService,
        List<Rule> initialRules, List<AccessTokenEvaluator> synchronousRules, List<SessionTokenEvaluator> beforeAndAfterRules,
        Map<String, PluggableServices> pluggableServicesMap)
        throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, InstantiationException,
        IllegalAccessException {
        for (Rule rule : initialRules) {
            Class<? extends AccessTokenEvaluator> newRuleClass =
                Class.forName(rule.getRuleClass()).asSubclass(AccessTokenEvaluator.class);
            AccessTokenEvaluator newRule = newRuleClass.getConstructor().newInstance();
            newRule.configure(systemOptions, rule.getRuleConfig());
            newRule.setConnectedSystem(connectedSystem);
            newRule.setTrackingService(sessionTrackingService);
            if (newRule instanceof SessionTokenEvaluator) {
                ((SessionTokenEvaluator) newRule).setPluggableServices(pluggableServicesMap);
                if (((SessionTokenEvaluator) newRule).isOnlySessionRule()) {
                    beforeAndAfterRules.add((SessionTokenEvaluator) newRule);
                } else {
                    log.info("Adding {} to synchronous rules", newRule.getClass().getName());
                    beforeAndAfterRules.add((SessionTokenEvaluator) newRule);
                    synchronousRules.add(newRule);
                }
            } else {
                log.info("Adding {} to synchronous rules", newRule.getClass().getName());
                synchronousRules.add(newRule);
            }
        }
    }

}
