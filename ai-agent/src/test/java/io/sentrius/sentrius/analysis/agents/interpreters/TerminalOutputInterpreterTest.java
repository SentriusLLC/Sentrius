package io.sentrius.sentrius.analysis.agents.interpreters;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.agent.analysis.agents.interpreters.TerminalListInterpreter;
import io.sentrius.agent.analysis.agents.interpreters.TerminalOutputInterpreter;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import io.sentrius.sso.core.utils.JsonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminalOutputInterpreterTest {

    @InjectMocks
    private TerminalOutputInterpreter terminalOutputInterpreter;

    @Mock
    private VerbResponse verbResponse;

    @Test
    void interpretReturnsValidResponseMapForValidInput() throws Exception {
        List<ObjectNode> validResponse = List.of(JsonUtil.MAPPER.createObjectNode().put("key", "value"));
        when(verbResponse.getResponse()).thenReturn(validResponse);

        Map<String, Object> result = terminalOutputInterpreter.interpret(verbResponse);

        assertNotNull(result);
        assertEquals("list", result.get("verb.response.type"));
        assertEquals("terminalOutput", result.get("verb.response.map.key"));
        assertEquals(ObjectNode.class.getCanonicalName(), result.get("verb.response.map.type"));
        List<ObjectNode> terminalOutput = (List<ObjectNode>) result.get("terminalOutput");
        assertEquals(1, terminalOutput.size());
        assertEquals("value", terminalOutput.get(0).get("key").asText());
    }

    @Test
    void interpretThrowsExceptionForNullResponse() {
        when(verbResponse.getResponse()).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> terminalOutputInterpreter.interpret(verbResponse));
    }

    @Test
    void interpretHandlesEmptyResponseList() throws Exception {
        when(verbResponse.getResponse()).thenReturn(List.of());

        Map<String, Object> result = terminalOutputInterpreter.interpret(verbResponse);

        assertNotNull(result);
        assertTrue(((List<?>) result.get("terminalOutput")).isEmpty());
    }

    @Test
    void interpretThrowsExceptionForInvalidResponseType() {
        when(verbResponse.getResponse()).thenReturn("Invalid response type");

        assertThrows(ClassCastException.class, () -> terminalOutputInterpreter.interpret(verbResponse));
    }
}