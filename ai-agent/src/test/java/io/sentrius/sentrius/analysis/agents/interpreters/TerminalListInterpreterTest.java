package io.sentrius.sentrius.analysis.agents.interpreters;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import io.sentrius.agent.analysis.agents.interpreters.TerminalListInterpreter;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.model.verbs.VerbResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminalListInterpreterTest {

    @InjectMocks
    private TerminalListInterpreter terminalListInterpreter;

    @Mock
    private VerbResponse verbResponse;

    @Test
    void interpretReturnsValidResponseMapForValidInput() throws Exception {
        String validJson = "[{\"id\":1,\"hostConnection\":\"connection1\"},{\"id\":2,\"hostConnection\":\"connection2\"}]";
        when(verbResponse.getResponse()).thenReturn(validJson);

        Map<String, Object> result = terminalListInterpreter.interpret(verbResponse);

        assertNotNull(result);
        assertEquals("list", result.get("verb.response.type"));
        assertEquals("terminals", result.get("verb.response.map.key"));
        assertEquals(HostSystemDTO.class.getCanonicalName(), result.get("verb.response.map.type"));
        List<HostSystemDTO> terminals = (List<HostSystemDTO>) result.get("terminals");
        assertEquals(2, terminals.size());
        assertEquals(1L, terminals.get(0).getId());
        assertEquals("connection1", terminals.get(0).getHostConnection());
    }

    @Test
    void interpretThrowsExceptionForInvalidJsonInput() {
        String invalidJson = "{\"id\":1,\"hostConnection\":\"connection1\"}";
        when(verbResponse.getResponse()).thenReturn(invalidJson);

        assertThrows(ClassCastException.class, () -> terminalListInterpreter.interpret(verbResponse));
    }

    @Test
    void interpretThrowsExceptionForMissingRequiredFields() {
        String jsonMissingFields = "[{\"id\":1},{\"hostConnection\":\"connection2\"}]";
        when(verbResponse.getResponse()).thenReturn(jsonMissingFields);

        assertThrows(IllegalArgumentException.class, () -> terminalListInterpreter.interpret(verbResponse));
    }

    @Test
    void interpretHandlesEmptyJsonArray() throws Exception {
        String emptyJsonArray = "[]";
        when(verbResponse.getResponse()).thenReturn(emptyJsonArray);

        Map<String, Object> result = terminalListInterpreter.interpret(verbResponse);

        assertNotNull(result);
        assertTrue(((List<?>) result.get("terminals")).isEmpty());
    }
}