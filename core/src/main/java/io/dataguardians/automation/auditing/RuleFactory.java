package io.dataguardians.automation.auditing;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.model.auditing.Rule;
import io.dataguardians.sso.core.services.PluggableServices;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuleFactory {

    public static void createRules(
        ConnectedSystem connectedSystem,
        SessionTrackingService sessionTrackingService,
        List<Rule> initialRules, List<AuditorRule> synchronousRules, List<SessionRuleIfc> beforeAndAfterRules,
        Map<String, PluggableServices> pluggableServicesMap)
        throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, InstantiationException,
        IllegalAccessException {
        for (Rule rule : initialRules) {
            Class<? extends AuditorRule> newRuleClass =
                Class.forName(rule.getRuleClass()).asSubclass(AuditorRule.class);
            AuditorRule newRule = newRuleClass.getConstructor().newInstance();
            newRule.configure(rule.getRuleConfig());
            newRule.setConnectedSystem(connectedSystem);
            newRule.setTrackingService(sessionTrackingService);
            if (newRule instanceof SessionRuleIfc) {
                ((SessionRuleIfc) newRule).setPluggableServices(pluggableServicesMap);
                if (((SessionRuleIfc) newRule).isOnlySessionRule()) {
                    beforeAndAfterRules.add((SessionRuleIfc) newRule);
                } else {
                    log.info("Adding {} to synchronous rules", newRule.getClass().getName());
                    beforeAndAfterRules.add((SessionRuleIfc) newRule);
                    synchronousRules.add(newRule);
                }
            } else {
                log.info("Adding {} to synchronous rules", newRule.getClass().getName());
                synchronousRules.add(newRule);
            }
        }
    }
}
