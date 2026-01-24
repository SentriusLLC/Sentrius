package io.sentrius.sentrius.analysis.agents.agents;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import io.sentrius.agent.analysis.agents.agents.AgentConfig;
import io.sentrius.agent.analysis.agents.agents.AgentVerb;
import io.sentrius.agent.analysis.agents.agents.PromptBuilder;
import io.sentrius.agent.analysis.agents.agents.VerbRegistry;
import io.sentrius.sso.core.model.verbs.Verb;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromptBuilderTest {

    @Mock
    private VerbRegistry verbRegistry;

    @Mock
    private AgentConfig agentConfig;

    @InjectMocks
    private PromptBuilder promptBuilder;

    //@Test
    void buildPromptIncludesRolesAndContext() {
        when(agentConfig.getRoles()).thenReturn(List.of("Admin", "User"));
        when(agentConfig.getContext()).thenReturn("System context");

        String result = promptBuilder.buildPrompt();

        assertTrue(result.contains("user"));
        assertTrue(result.contains("Context: System context"));
    }

    @Test
    void buildPromptIncludesAvailableVerbs() {
        AgentVerb verb = mock(AgentVerb.class);
        Method mockMethod = Verb.class.getDeclaredMethods()[0];
        when(verb.getMethod()).thenReturn(mockMethod);
        when(verb.getDescription()).thenReturn("Description of the verb");
        // Use a verb lookup verb name to match the new implementation
        when(verbRegistry.getVerbs()).thenReturn(Map.of("search_verbs", verb));

        String result = promptBuilder.buildPrompt();

        assertTrue(result.contains("VERB DISCOVERY:"));
        assertTrue(result.contains("- search_verbs ("));
        assertTrue(result.contains("Description of the verb"));
    }

    //@Test
    void buildPromptHandlesEmptyRolesAndContext() {
        when(agentConfig.getRoles()).thenReturn(List.of());
        when(agentConfig.getContext()).thenReturn("");

        String result = promptBuilder.buildPrompt();

        assertTrue(result.contains("Context: "));
    }

    @Test
    void buildPromptHandlesNoAvailableVerbs() {
        when(verbRegistry.getVerbs()).thenReturn(Map.of());

        String result = promptBuilder.buildPrompt();

        // The prompt now uses "VERB DISCOVERY:" instead of "Verb operations:"
        assertTrue(result.contains("VERB DISCOVERY:"));
    }
}