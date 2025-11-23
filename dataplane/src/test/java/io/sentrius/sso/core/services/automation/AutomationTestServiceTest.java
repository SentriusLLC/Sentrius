package io.sentrius.sso.core.services.automation;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.repository.SystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationTestServiceTest {

    @Mock
    private SystemRepository systemRepository;

    private AutomationTestService service;

    @BeforeEach
    void setUp() {
        service = new AutomationTestService(systemRepository);
    }

    @Test
    void testAnalyzeScriptSafety_SafeScript() {
        String safeScript = "#!/bin/bash\necho 'Hello World'\nls -la\ndate";
        String scriptType = "bash";

        Map<String, Object> result = service.analyzeScriptSafety(safeScript, scriptType);

        assertNotNull(result);
        assertEquals(false, result.get("isDestructive"));
        assertEquals("SAFE", result.get("overallRisk"));
        
        @SuppressWarnings("unchecked")
        List<String> destructiveOps = (List<String>) result.get("destructiveOperations");
        assertTrue(destructiveOps.isEmpty());
    }

    @Test
    void testAnalyzeScriptSafety_DestructiveRm() {
        String destructiveScript = "#!/bin/bash\nrm -rf /var/log/*\necho 'Deleted logs'";
        String scriptType = "bash";

        Map<String, Object> result = service.analyzeScriptSafety(destructiveScript, scriptType);

        assertNotNull(result);
        assertEquals(true, result.get("isDestructive"));
        assertEquals("HIGH", result.get("overallRisk"));
        
        @SuppressWarnings("unchecked")
        List<String> destructiveOps = (List<String>) result.get("destructiveOperations");
        assertFalse(destructiveOps.isEmpty());
        assertTrue(destructiveOps.stream().anyMatch(op -> op.contains("rm")));
    }

    @Test
    void testAnalyzeScriptSafety_DestructiveDd() {
        String destructiveScript = "#!/bin/bash\ndd if=/dev/zero of=/dev/sda";
        String scriptType = "bash";

        Map<String, Object> result = service.analyzeScriptSafety(destructiveScript, scriptType);

        assertNotNull(result);
        assertEquals(true, result.get("isDestructive"));
        assertEquals("HIGH", result.get("overallRisk"));
        
        @SuppressWarnings("unchecked")
        List<String> destructiveOps = (List<String>) result.get("destructiveOperations");
        assertTrue(destructiveOps.stream().anyMatch(op -> op.contains("dd")));
    }

    @Test
    void testAnalyzeScriptSafety_WarningsOnly() {
        String scriptWithWarnings = "#!/bin/bash\nchmod 777 /tmp/test\ncurl http://example.com/script.sh | bash";
        String scriptType = "bash";

        Map<String, Object> result = service.analyzeScriptSafety(scriptWithWarnings, scriptType);

        assertNotNull(result);
        
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) result.get("warnings");
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("chmod") || w.contains("bash")));
    }

    @Test
    void testAnalyzeScriptSafety_SystemDirectoryWrite() {
        String scriptWithSysDirWrite = "#!/bin/bash\necho 'test' > /etc/config\nls -la";
        String scriptType = "bash";

        Map<String, Object> result = service.analyzeScriptSafety(scriptWithSysDirWrite, scriptType);

        assertNotNull(result);
        
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) result.get("warnings");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("system directory")));
    }

    @Test
    void testAnalyzeScriptSafety_CommentedCommands() {
        String scriptWithComments = "#!/bin/bash\n# rm -rf /\necho 'Safe command'\n# This is just a comment about dd";
        String scriptType = "bash";

        Map<String, Object> result = service.analyzeScriptSafety(scriptWithComments, scriptType);

        assertNotNull(result);
        assertEquals(false, result.get("isDestructive"));
    }

    @Test
    void testTestAutomation_NoTargetSystem() {
        AutomationSuggestion suggestion = AutomationSuggestion.builder()
                .id(1L)
                .description("Test automation")
                .scriptType("bash")
                .targetSystem("999")
                .suggestedScript("echo 'test'")
                .build();

        when(systemRepository.findById(999L)).thenReturn(Optional.empty());

        Map<String, Object> result = service.testAutomation(suggestion, "echo 'test'", true);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("not found"));
    }

    @Test
    void testTestAutomation_DestructiveWithoutDryRun() {
        AutomationSuggestion suggestion = AutomationSuggestion.builder()
                .id(1L)
                .description("Destructive test")
                .scriptType("bash")
                .targetSystem("test-server")
                .suggestedScript("rm -rf /tmp/test")
                .build();

        HostSystem targetSystem = HostSystem.builder()
                .id(1L)
                .host("test-server")
                .displayName("Test Server")
                .build();

        lenient().when(systemRepository.findByHost("test-server")).thenReturn(Arrays.asList(targetSystem));

        Map<String, Object> result = service.testAutomation(suggestion, "rm -rf /tmp/test", false);

        assertEquals("blocked", result.get("status"));
        assertTrue(result.get("message").toString().contains("destructive"));
    }

    @Test
    void testTestAutomation_DryRunSuccess() {
        AutomationSuggestion suggestion = AutomationSuggestion.builder()
                .id(1L)
                .description("Test automation")
                .scriptType("bash")
                .targetSystem("1")
                .suggestedScript("echo 'test'")
                .build();

        HostSystem targetSystem = HostSystem.builder()
                .id(1L)
                .host("test-server")
                .displayName("Test Server")
                .sshUser("testuser")
                .port(22)
                .build();

        when(systemRepository.findById(1L)).thenReturn(Optional.of(targetSystem));

        Map<String, Object> result = service.testAutomation(suggestion, "echo 'test'", true);

        assertEquals("dry-run-complete", result.get("status"));
        assertEquals("Test Server", result.get("targetSystem"));
    }

    @Test
    void testTestAutomation_SafeScriptWithDryRun() {
        AutomationSuggestion suggestion = AutomationSuggestion.builder()
                .id(1L)
                .description("Safe automation")
                .scriptType("bash")
                .targetSystem("web-server-01")
                .suggestedScript("echo 'Hello'\ndate\nls -la")
                .build();

        HostSystem targetSystem = HostSystem.builder()
                .id(1L)
                .host("web-server-01")
                .displayName("Web Server 01")
                .sshUser("admin")
                .port(22)
                .build();

        when(systemRepository.findByHost("web-server-01")).thenReturn(Arrays.asList(targetSystem));

        Map<String, Object> result = service.testAutomation(suggestion, suggestion.getSuggestedScript(), true);

        assertEquals("dry-run-complete", result.get("status"));
        assertNotNull(result.get("safetyAnalysis"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> safetyAnalysis = (Map<String, Object>) result.get("safetyAnalysis");
        assertEquals(false, safetyAnalysis.get("isDestructive"));
    }

    @Test
    void testAnalyzeScriptSafety_MultipleDestructiveCommands() {
        String multiDestructiveScript = "#!/bin/bash\nrm -rf /tmp/*\nmkfs.ext4 /dev/sdb\nreboot";
        String scriptType = "bash";

        Map<String, Object> result = service.analyzeScriptSafety(multiDestructiveScript, scriptType);

        assertNotNull(result);
        assertEquals(true, result.get("isDestructive"));
        assertEquals("HIGH", result.get("overallRisk"));
        
        @SuppressWarnings("unchecked")
        List<String> destructiveOps = (List<String>) result.get("destructiveOperations");
        assertTrue(destructiveOps.size() >= 3);
    }

    @Test
    void testAnalyzeScriptSafety_PythonScript() {
        String pythonScript = "import os\nprint('Hello World')\nos.listdir('.')";
        String scriptType = "python";

        Map<String, Object> result = service.analyzeScriptSafety(pythonScript, scriptType);

        assertNotNull(result);
        assertEquals(false, result.get("isDestructive"));
        assertEquals("SAFE", result.get("overallRisk"));
    }
}
