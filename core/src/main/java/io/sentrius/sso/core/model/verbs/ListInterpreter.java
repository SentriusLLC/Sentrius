package io.sentrius.sso.core.model.verbs;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.sso.core.utils.JsonUtil;

public class ListInterpreter<T> implements InputInterpreterIfc<List<T>>{
    @Override
    public List<T> interpret(Map<String, Object> input) throws Exception {
        if (input.containsKey("verb.response.type") && input.get("verb.response.type").equals("list")) {
            return interpretList(input);
        } else if (input.containsKey("verb.response.type") && input.get("verb.response.type").equals(ArrayNode.class.getCanonicalName())) {
            var str = input.get("verb.response").toString();
            ArrayNode node = (ArrayNode) JsonUtil.MAPPER.readTree(str);
            if (node == null) {
                throw new IllegalArgumentException("Input response is not a valid JSON array");
            }
            TypeReference<List<T>> typeRef = new TypeReference<>() {};
            return JsonUtil.convertArrayNodeToList(node,typeRef);
        } else {
            throw new IllegalArgumentException("Invalid input type " + input.get("verb.response.type"));
        }

    }


    private List<T> interpretList(Map<String, Object> input) {

        var field = input.get("verb.response.map.key");
        if (field == null) {
            throw new IllegalArgumentException("Input response does not contain required fields");
        }

        var object = input.get(field);
        if (object instanceof List){
            return (List<T>) object;
        }
        throw new IllegalArgumentException("Input response does not contain required fields");
    }
}
