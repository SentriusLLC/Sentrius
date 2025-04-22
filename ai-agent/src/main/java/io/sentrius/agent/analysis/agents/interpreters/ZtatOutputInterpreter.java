package io.sentrius.agent.analysis.agents.interpreters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.dto.ztat.AtatRequest;
import io.sentrius.sso.core.model.verbs.ListInterpreter;
import io.sentrius.sso.core.model.verbs.OutputInterpreterIfc;
import io.sentrius.sso.core.model.verbs.VerbResponse;

/**
 * The `TerminalOutputInterpreter` class is responsible for interpreting the response
 * of a terminal output verb and converting it into a structured map.
 * It extends the `ListInterpreter` class and implements the `OutputInterpreterIfc` interface.
 */
public class ZtatOutputInterpreter extends ListInterpreter<List<AtatRequest>> implements OutputInterpreterIfc {

    /**
     * Interprets the given `VerbResponse` input and converts it into a structured map.
     *
     * @param input The `VerbResponse` object containing the response to interpret.
     * @return A map containing the interpreted response with keys for type, map key, map type, and the terminal output.
     * @throws Exception If the input response is null or not a valid JSON array.
     */
    @Override
    public Map<String, Object> interpret(VerbResponse input) throws Exception {
        // Create a map to store the interpreted response
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("verb.response.type", "list");
        responseMap.put("verb.response.map.key", "terminalOutput");
        responseMap.put("verb.response.map.type", AtatRequest.class.getCanonicalName());

        // Extract the response as a list of ObjectNode
        List<AtatRequest> list = (List<AtatRequest>) input.getResponse();
        if (list == null) {
            throw new IllegalArgumentException("Input response is not a valid JSON array");
        }

        // Add the terminal output to the response map
        responseMap.put("terminalOutput", list);
        return responseMap;
    }
}