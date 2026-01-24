package io.sentrius.sentrius.analysis.agents.verbs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.agent.analysis.agents.verbs.TerminalVerbs;
import io.sentrius.sso.core.dto.HostSystemDTO;
import io.sentrius.sso.core.dto.agents.AgentExecutionContextDTO;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.LLMService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.utils.JsonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminalVerbsTest {

    @Mock
    private ZeroTrustClientService zeroTrustClientService;

    @Mock
    private LLMService llmService;

    @InjectMocks
    private TerminalVerbs terminalVerbs;

  //  @Test
    void listTerminalsReturnsArrayNodeWhenApiCallSucceeds() throws Exception, ZtatException {
        String mockResponse = "[{\"id\":1,\"name\":\"Terminal1\"},{\"id\":2,\"name\":\"Terminal2\"}]";

        when(zeroTrustClientService.callGetOnApi(isNull(), "/ssh/terminal/list/all")).thenReturn(mockResponse);
        ArrayNode result = terminalVerbs.listTerminals(null, AgentExecutionContextDTO.builder().build());

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Terminal1", result.get(0).get("name").asText());
    }

   // @Test
    void listTerminalsThrowsRuntimeExceptionWhenApiCallFails() throws ZtatException {
        when(zeroTrustClientService.callGetOnApi(isNull(), "/ssh/terminal/list/all")).thenThrow(new RuntimeException("API error"));

        assertThrows(RuntimeException.class, () -> terminalVerbs.listTerminals(null, AgentExecutionContextDTO.builder().build()));
    }

  //  @Test
    void fetchTerminalOutputReturnsListOfObjectNodesWhenApiCallSucceeds() throws Exception, ZtatException {
        HostSystemDTO dto = new HostSystemDTO();
        dto.setId(1L);
        dto.setHostConnection("connection1");

        String mockResponse = "Terminal output logs";
        when(zeroTrustClientService.callGetOnApi(isNull(),eq("/sessions/audit/attach"),
            ArgumentMatchers.any(Map.Entry.class))).thenReturn(mockResponse);


        AgentExecutionContextDTO context = AgentExecutionContextDTO.builder()
            .build();

        List<ObjectNode> result = terminalVerbs.fetchTerminalOutput(null, context);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("1", result.get(0).get("id").asText());
        assertEquals("Terminal output logs", result.get(0).get("terminalOutput").asText());
    }

    @Test
    void fetchTerminalOutputHandlesEmptyDtosList() throws Exception, ZtatException {
        // When no terminals are provided, the method throws RuntimeException (wrapping IllegalArgumentException)
        // to guide the agent on proper usage
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            terminalVerbs.fetchTerminalOutput(null, AgentExecutionContextDTO.builder().build()));
        // Verify the root cause is IllegalArgumentException with helpful message
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertTrue(exception.getCause().getMessage().contains("No terminals provided to fetch logs from"));
    }

    @Test
    void fetchTerminalOutputThrowsRuntimeExceptionWhenApiCallFails() throws ZtatException {
        HostSystemDTO dto = new HostSystemDTO();
        dto.setId(1L);
        dto.setHostConnection("connection1");
        List<HostSystemDTO> dtos = List.of(dto);

        AgentExecutionContextDTO context = AgentExecutionContextDTO.builder()
            .build();

        context.addToMemory("terminals", JsonUtil.MAPPER.valueToTree(dtos));
        when(zeroTrustClientService.callGetOnApi(isNull(), eq("/sessions/audit/attach"),
            ArgumentMatchers.any(Map.Entry.class))).thenThrow(new RuntimeException(
            "API error"));

        assertThrows(RuntimeException.class, () -> terminalVerbs.fetchTerminalOutput(null, context));
    }
}