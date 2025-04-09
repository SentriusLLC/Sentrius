package io.sentrius.agent.analysis.agents.interpreters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.model.verbs.InputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.ListInterpreter;
import io.sentrius.sso.core.model.verbs.OutputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.utils.JsonUtil;

public class TerminalOutputInterpreter extends ListInterpreter<ObjectNode> implements OutputInterpreterIfc {

    @Override
    public Map<String, Object> interpret(VerbResponse input) throws Exception {
        // Assuming input.response is a list of strings
        Map<String,Object> responseMap = new HashMap<>();
        responseMap.put("verb.response.type", "list");
        responseMap.put("verb.response.map.key", "terminalOutput");
        responseMap.put("verb.response.map.type", ObjectNode.class.getCanonicalName());

        List<ObjectNode> list = (List<ObjectNode>) input.getResponse();
        if (list == null) {
            throw new IllegalArgumentException("Input response is not a valid JSON array");
        }
        responseMap.put("terminalOutput",list);
       return responseMap;
    }

}
