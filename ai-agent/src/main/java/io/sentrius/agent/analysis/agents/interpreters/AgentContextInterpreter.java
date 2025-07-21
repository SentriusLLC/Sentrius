package io.sentrius.agent.analysis.agents.interpreters;

import java.util.Map;
import io.sentrius.sso.core.dto.agents.AgentContextDTO;
import io.sentrius.sso.core.model.verbs.InputInterpreterIfc;
import io.sentrius.sso.core.utils.JsonUtil;


public class AgentContextInterpreter implements InputInterpreterIfc<AgentContextDTO> {
    @Override
    public AgentContextDTO interpret(Map<String,Object> input) throws Exception {
        Object agentContextObj = input.get("agentContext");
        Object arg1Obj = input.get("arg1");

        if (agentContextObj != null) {
            return JsonUtil.MAPPER.convertValue(agentContextObj, AgentContextDTO.class);
        } else if (arg1Obj != null) {
            return JsonUtil.MAPPER.convertValue(arg1Obj, AgentContextDTO.class);
        }
        return null;
    }
}
