package io.sentrius.agent.analysis.agents.interpreters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.agent.analysis.model.AssessedTerminal;
import io.sentrius.agent.analysis.model.Assessment;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.model.verbs.ListInterpreter;
import io.sentrius.sso.core.model.verbs.OutputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsessmentListInterpreter extends ListInterpreter<AssessedTerminal> implements OutputInterpreterIfc {

    @Override
    public Map<String, Object> interpret(VerbResponse input) throws Exception {
        log.info("AssessmentListInterpreter: interpret() called with input: {}", input);
        // Assuming input.response is a list of strings
        Map<String,Object> responseMap = new HashMap<>();
        responseMap.put("verb.response.type", "list");
        responseMap.put("verb.response.map.key", "assessments");
        responseMap.put("verb.response.map.type", AssessedTerminal.class.getCanonicalName());

        if (input.getResponse() instanceof List<?> list) {
            log.info("AssessmentListInterpreter: interpret() called with input list");
            if (list.isEmpty() || list.get(0) instanceof AssessedTerminal) {

                responseMap.put("assessments", list);
            } else {
                throw new IllegalArgumentException("Input response is not a List of Assessment objects");
            }
        } else {
                throw new IllegalArgumentException("Input response does not contain required fields");
        }

        return responseMap;
    }

}
