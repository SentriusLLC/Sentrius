package io.sentrius.agent.analysis.agents.interpreters;

import java.util.Map;
import io.sentrius.sso.core.model.verbs.InputInterpreterIfc;
import io.sentrius.sso.core.trust.ATPLPolicy;
import io.sentrius.sso.core.utils.JsonUtil;

public class StringInterpreter implements InputInterpreterIfc<ATPLPolicy> {

    @Override
    public ATPLPolicy interpret(Map<String,Object> input) throws Exception {
        Object policyObj = input.get("policy");
        Object arg1Obj = input.get("arg1");

        if (policyObj != null) {
            return JsonUtil.MAPPER.convertValue(policyObj, ATPLPolicy.class);
        } else if (arg1Obj != null) {
            return JsonUtil.MAPPER.convertValue(arg1Obj, ATPLPolicy.class);
        }
        return null;
    }
}
