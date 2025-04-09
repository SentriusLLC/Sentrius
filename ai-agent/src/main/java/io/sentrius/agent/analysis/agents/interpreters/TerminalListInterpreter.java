package io.sentrius.agent.analysis.agents.interpreters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.model.verbs.InputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.ListInterpreter;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.model.verbs.OutputInterpreterIfc;
import io.sentrius.sso.core.utils.JsonUtil;

public class TerminalListInterpreter extends ListInterpreter<HostSystemDTO> implements OutputInterpreterIfc {

    @Override
    public Map<String, Object> interpret(VerbResponse input) throws Exception {
        // Assuming input.response is a list of strings
        Map<String,Object> responseMap = new HashMap<>();
        responseMap.put("verb.response.type", "list");
        responseMap.put("verb.response.map.key", "terminals");
        responseMap.put("verb.response.map.type", HostSystemDTO.class.getCanonicalName());
        var str = input.getResponse().toString();
        ArrayNode node = (ArrayNode) JsonUtil.MAPPER.readTree(str);
        if (node == null) {
            throw new IllegalArgumentException("Input response is not a valid JSON array");
        }
        List<HostSystemDTO> list = new ArrayList<>();
        for(int i = 0; i < node.size(); i++) {
            var item = node.get(i);
            if (item.has("id") && item.has("hostConnection")) {
                HostSystemDTO hostSystemDTO = new HostSystemDTO();
                hostSystemDTO.setId(item.get("id").asLong());
                hostSystemDTO.setHostConnection(item.get("hostConnection").asText());
                list.add(hostSystemDTO);
            } else {
                throw new IllegalArgumentException("Input response does not contain required fields");
            }
        }
        responseMap.put("terminals",list);
        return responseMap;
    }

}
