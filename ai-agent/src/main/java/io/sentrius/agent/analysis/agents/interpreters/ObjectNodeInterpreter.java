package io.sentrius.agent.analysis.agents.interpreters;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.model.verbs.InputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.OutputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ObjectNodeInterpreter implements InputInterpreterIfc<ObjectNode>, OutputInterpreterIfc {
    @Override
    public ObjectNode interpret(Map<String, Object> input) throws Exception {
        Object raw = input.containsKey("arg1") ? input.get("arg1") : input;

        JsonNode node = JsonUtil.MAPPER.valueToTree(raw);

        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Input is null or could not be converted to ObjectNode");
        }

        if (!node.isObject()) {
            throw new IllegalArgumentException("Expected ObjectNode, got: " + node.getNodeType());
        }

        return (ObjectNode) node;

    }

    @Override
    public Map<String, Object> interpret(VerbResponse input) throws Exception {
        if (input.getResponse() instanceof ObjectNode) {
            Map<String, Object> responseMap = new HashMap<>();
            ((ObjectNode)input.getResponse()).fieldNames().forEachRemaining( x ->{
                    responseMap.put(x, ((ObjectNode)input.getResponse()).get(x).asText() );
                });
            return responseMap;
        }
        return Map.of();
    }
}
