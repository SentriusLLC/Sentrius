package io.sentrius.sentrius.analysis.agents.verbs;

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

    @Test
    void buildPromptIncludesRolesAndContext() {
        when(agentConfig.getRoles()).thenReturn(List.of("Admin", "User"));
        when(agentConfig.getContext()).thenReturn("System context");

        String result = promptBuilder.buildPrompt();

        assertTrue(result.contains("Roles: Admin, User"));
        assertTrue(result.contains("Context: System context"));
    }

    @Test
    void buildPromptIncludesAvailableVerbs() {
        AgentVerb verb = mock(AgentVerb.class);
        Method mockMethod = Verb.class.getDeclaredMethods()[0];
        when(verb.getMethod()).thenReturn(mockMethod);
        when(verb.getDescription()).thenReturn("Description of the verb");
        when(verbRegistry.getVerbs()).thenReturn(Map.of("verbName", verb));

        String result = promptBuilder.buildPrompt();

        assertTrue(result.contains("Available Verbs:"));
        assertTrue(result.contains("- verbName ("));
        assertTrue(result.contains("Description of the verb"));
    }

    @Test
    void buildPromptHandlesEmptyRolesAndContext() {
        when(agentConfig.getRoles()).thenReturn(List.of());
        when(agentConfig.getContext()).thenReturn("");

        String result = promptBuilder.buildPrompt();

        assertTrue(result.contains("Roles: "));
        assertTrue(result.contains("Context: "));
    }

    @Test
    void buildPromptHandlesNoAvailableVerbs() {
        when(verbRegistry.getVerbs()).thenReturn(Map.of());

        String result = promptBuilder.buildPrompt();

        assertTrue(result.contains("Available Verbs:"));
        assertFalse(result.contains("- "));
    }
}