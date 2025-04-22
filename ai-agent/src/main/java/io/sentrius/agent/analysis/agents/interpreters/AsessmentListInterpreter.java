package io.sentrius.agent.analysis.agents.interpreters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.agent.analysis.model.Assessment;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.model.verbs.ListInterpreter;
import io.sentrius.sso.core.model.verbs.OutputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsessmentListInterpreter extends ListInterpreter<Assessment> implements OutputInterpreterIfc {

    @Override
    public Map<String, Object> interpret(VerbResponse input) throws Exception {
        log.info("AssessmentListInterpreter: interpret() called with input: {}", input);
        // Assuming input.response is a list of strings
        Map<String,Object> responseMap = new HashMap<>();
        responseMap.put("verb.response.type", "list");
        responseMap.put("verb.response.map.key", "assessments");
        responseMap.put("verb.response.map.type", Assessment.class.getCanonicalName());

        if (input.getResponse() instanceof List<?> list) {
            log.info("AssessmentListInterpreter: interpret() called with input list");
            if (list.isEmpty() || list.get(0) instanceof Assessment) {

                responseMap.put("assessments", list);
            } else {
                throw new IllegalArgumentException("Input response is not a List of Assessment objects");
            }
        } else {

            var str = input.getResponse().toString();
            log.info("AssessmentListInterpreter: interpret() called with input string {} " ,str);
            ArrayNode node = (ArrayNode) JsonUtil.MAPPER.readTree(str);
            if (node == null) {
                throw new IllegalArgumentException("Input response is not a valid JSON array");
            }
            List<Assessment> list = new ArrayList<>();
            for (int i = 0; i < node.size(); i++) {
                var item = node.get(i);
                if (item.has("sessionId") && item.has("risk") && item.has("description")) {
                    Assessment hostSystemDTO = new Assessment();
                    hostSystemDTO.setSessionId(item.get("sessionId").asText());
                    hostSystemDTO.setDescription(item.get("description").asText());
                    hostSystemDTO.setRisk(item.get("risk").asText());
                    list.add(hostSystemDTO);
                } else {
                    throw new IllegalArgumentException("Input response does not contain required fields");
                }
            }
            responseMap.put("assessments",list);
        }

        return responseMap;
    }

}
