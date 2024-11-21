package io.dataguardians.automation.auditing;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.model.auditing.Rule;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;

public class RuleFactory {

    public static void createRules(
        ConnectedSystem connectedSystem,
        SessionTrackingService sessionTrackingService,
        List<Rule> initialRules, List<AuditorRule> synchronousRules, List<SessionRuleIfc> beforeAndAfterRules)
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
                if (((SessionRuleIfc) newRule).isOnlySessionRule()) {
                    beforeAndAfterRules.add((SessionRuleIfc) newRule);
                } else {
                    synchronousRules.add(newRule);
                }
            } else {
                synchronousRules.add(newRule);
            }
        }
    }
}
