package io.sentrius.sso.core.services.automation;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.automation.Automation;
import io.sentrius.sso.core.model.automation.AutomationExecution;
import io.sentrius.sso.core.model.automation.AutomationSuggestion;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.SystemRepository;
import io.sentrius.sso.core.repository.automation.ScriptExecutionRepository;
import io.sentrius.sso.core.repository.automation.ScriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AutomationExecutionService
 */
@ExtendWith(MockitoExtension.class)
class AutomationExecutionServiceTest {

    @Mock
    private ScriptRepository scriptRepository;

    @Mock
    private ScriptExecutionRepository scriptExecutionRepository;

    @Mock
    private SystemRepository systemRepository;

    @Mock
    private FileTransferService fileTransferService;

    @InjectMocks
    private AutomationExecutionService executionService;

    private Automation testAutomation;
    private AutomationSuggestion testSuggestion;
    private HostSystem testSystem;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testAutomation = new Automation();
        testAutomation.setId(1L);
        testAutomation.setDisplayName("Test Automation");
        testAutomation.setScript("#!/bin/bash\necho 'Hello World'");
        testAutomation.setType("bash");
        testAutomation.setUser(testUser);

        testSuggestion = AutomationSuggestion.builder()
                .id(1L)
                .suggestedScript("#!/bin/bash\necho 'Hello from suggestion'")
                .description("Test suggestion")
                .scriptType("bash")
                .status("APPROVED")
                .build();

        testSystem = new HostSystem();
        testSystem.setId(1L);
        testSystem.setDisplayName("Test System");
        testSystem.setHost("test.example.com");
        testSystem.setPort(22);
        testSystem.setSshUser("testuser");
    }

    @Test
    void testExecuteAutomationOnSystem_AutomationNotFound() {
        when(scriptRepository.findById(1L)).thenReturn(Optional.empty());

        Map<String, Object> result = executionService.executeAutomationOnSystem(1L, 1L, testUser);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("Automation not found"));
    }

    @Test
    void testExecuteAutomationOnSystem_SystemNotFound() {
        when(scriptRepository.findById(1L)).thenReturn(Optional.of(testAutomation));
        when(systemRepository.findById(1L)).thenReturn(Optional.empty());

        Map<String, Object> result = executionService.executeAutomationOnSystem(1L, 1L, testUser);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("System not found"));
    }

    @Test
    void testExecuteAutomationOnSystem_TransferFailure() {
        when(scriptRepository.findById(1L)).thenReturn(Optional.of(testAutomation));
        when(systemRepository.findById(1L)).thenReturn(Optional.of(testSystem));

        AutomationExecution mockExecution = AutomationExecution.builder()
                .id(1L)
                .automation(testAutomation)
                .system(testSystem)
                .executedBy(testUser)
                .status("PENDING")
                .build();
        when(scriptExecutionRepository.save(any(AutomationExecution.class))).thenReturn(mockExecution);

        Map<String, Object> transferResult = new HashMap<>();
        transferResult.put("status", "error");
        transferResult.put("message", "Connection refused");
        when(fileTransferService.transferScriptToSystem(any(), any(), any())).thenReturn(transferResult);

        Map<String, Object> result = executionService.executeAutomationOnSystem(1L, 1L, testUser);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("Failed to transfer script"));
        verify(scriptExecutionRepository, atLeastOnce()).save(any(AutomationExecution.class));
    }

    @Test
    void testGetExecutionHistory() {
        List<AutomationExecution> executions = new ArrayList<>();
        
        AutomationExecution exec1 = AutomationExecution.builder()
                .id(1L)
                .automation(testAutomation)
                .system(testSystem)
                .executedBy(testUser)
                .status("SUCCESS")
                .exitCode(0)
                .logTm(new java.sql.Timestamp(System.currentTimeMillis() - 1000))
                .build();
        
        AutomationExecution exec2 = AutomationExecution.builder()
                .id(2L)
                .automation(testAutomation)
                .system(testSystem)
                .executedBy(testUser)
                .status("FAILED")
                .exitCode(1)
                .logTm(new java.sql.Timestamp(System.currentTimeMillis()))
                .build();
        
        executions.add(exec2);  // Most recent first (already sorted by database)
        executions.add(exec1);

        when(scriptExecutionRepository.findByAutomationIdOrderByLogTmDesc(1L)).thenReturn(executions);

        List<AutomationExecution> result = executionService.getExecutionHistory(1L);

        assertNotNull(result);
        assertEquals(2, result.size());
        // Should be sorted by timestamp descending (most recent first)
        assertEquals(2L, result.get(0).getId());
        assertEquals(1L, result.get(1).getId());
    }

    @Test
    void testGetExecutionById() {
        AutomationExecution execution = AutomationExecution.builder()
                .id(1L)
                .automation(testAutomation)
                .system(testSystem)
                .executedBy(testUser)
                .status("SUCCESS")
                .build();

        when(scriptExecutionRepository.findById(1L)).thenReturn(Optional.of(execution));

        Optional<AutomationExecution> result = executionService.getExecutionById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("SUCCESS", result.get().getStatus());
    }

    @Test
    void testGetExecutionById_NotFound() {
        when(scriptExecutionRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<AutomationExecution> result = executionService.getExecutionById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void testExecuteSuggestionOnSystem_SystemNotFound() {
        when(systemRepository.findById(1L)).thenReturn(Optional.empty());

        Map<String, Object> result = executionService.executeSuggestionOnSystem(1L, 1L, testUser, testSuggestion);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("System not found"));
    }

    @Test
    void testExecuteSuggestionOnSystem_EmptyScript() {
        // Create a fresh suggestion with empty script to avoid modifying shared fixture
        AutomationSuggestion emptySuggestion = AutomationSuggestion.builder()
                .id(1L)
                .suggestedScript("")
                .description("Test suggestion with empty script")
                .scriptType("bash")
                .status("APPROVED")
                .build();
        
        when(systemRepository.findById(1L)).thenReturn(Optional.of(testSystem));

        Map<String, Object> result = executionService.executeSuggestionOnSystem(1L, 1L, testUser, emptySuggestion);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("no script to execute"));
    }

    @Test
    void testExecuteSuggestionOnSystem_TransferFailure() {
        when(systemRepository.findById(1L)).thenReturn(Optional.of(testSystem));

        AutomationExecution mockExecution = AutomationExecution.builder()
                .id(1L)
                .suggestion(testSuggestion)
                .system(testSystem)
                .executedBy(testUser)
                .status("PENDING")
                .build();
        when(scriptExecutionRepository.save(any(AutomationExecution.class))).thenReturn(mockExecution);

        Map<String, Object> transferResult = new HashMap<>();
        transferResult.put("status", "error");
        transferResult.put("message", "Connection refused");
        when(fileTransferService.transferScriptToSystem(any(), any(), any())).thenReturn(transferResult);

        Map<String, Object> result = executionService.executeSuggestionOnSystem(1L, 1L, testUser, testSuggestion);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("Failed to transfer script"));
        verify(scriptExecutionRepository, atLeastOnce()).save(any(AutomationExecution.class));
    }

    @Test
    void testGetSuggestionExecutionHistory() {
        List<AutomationExecution> executions = new ArrayList<>();
        
        AutomationExecution exec1 = AutomationExecution.builder()
                .id(1L)
                .suggestion(testSuggestion)
                .system(testSystem)
                .executedBy(testUser)
                .status("SUCCESS")
                .exitCode(0)
                .logTm(new java.sql.Timestamp(System.currentTimeMillis() - 1000))
                .build();
        
        AutomationExecution exec2 = AutomationExecution.builder()
                .id(2L)
                .suggestion(testSuggestion)
                .system(testSystem)
                .executedBy(testUser)
                .status("FAILED")
                .exitCode(1)
                .logTm(new java.sql.Timestamp(System.currentTimeMillis()))
                .build();
        
        executions.add(exec2);  // Most recent first (already sorted by database)
        executions.add(exec1);

        when(scriptExecutionRepository.findBySuggestionIdOrderByLogTmDesc(1L)).thenReturn(executions);

        List<AutomationExecution> result = executionService.getSuggestionExecutionHistory(1L);

        assertNotNull(result);
        assertEquals(2, result.size());
        // Should be sorted by timestamp descending (most recent first)
        assertEquals(2L, result.get(0).getId());
        assertEquals(1L, result.get(1).getId());
    }
}
