package io.sentrius.sso.core.model.verbs;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultInterpreter implements OutputInterpreterIfc, InputInterpreterIfc<Map<String, Object>> {

    @Override
    public Map<String, Object> interpret(VerbResponse input) throws Exception {
        // Default implementation: return the response as is
        Map<String,Object> responseMap = new HashMap<>();
        responseMap.put("verb.response.type", input.getReturnType().getCanonicalName());
        responseMap.put("verb.response", input.getResponse());
        return responseMap;
    }

    @Override
    public Map<String, Object> interpret(Map<String, Object> input) throws Exception {
        return input;
    }
}
