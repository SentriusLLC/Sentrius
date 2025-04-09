package io.sentrius.sso.core.model.verbs;

import java.util.List;
import java.util.Map;

public class ListInterpreter<T> implements InputInterpreterIfc<List<T>>{
    @Override
    public List<T> interpret(Map<String, Object> input) throws Exception {
        if (input.containsKey("verb.response.type") && input.get("verb.response.type").equals("list")) {
            return interpretList(input);
        } else {
            throw new IllegalArgumentException("Invalid input type");
        }

    }


    private List<T> interpretList(Map<String, Object> input) {

        var field = input.get("verb.response.map.key");;
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
