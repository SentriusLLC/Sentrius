package io.sentrius.sso.core.services.automation;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.automation.Automation;
import io.sentrius.sso.core.model.automation.AutomationAssignment;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.SystemRepository;
import io.sentrius.sso.core.repository.automation.ScriptAssignmentRepository;
import io.sentrius.sso.core.repository.automation.ScriptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationAssignmentServiceTest {

    @Mock
    private ScriptAssignmentRepository assignmentRepository;

    @Mock
    private ScriptRepository scriptRepository;

    @Mock
    private SystemRepository systemRepository;

    private AutomationAssignmentService service;

    private Automation testAutomation;
    private HostSystem testSystem;
    private User testUser;

    @BeforeEach
    void setUp() {
        service = new AutomationAssignmentService(assignmentRepository, scriptRepository, systemRepository);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testAutomation = new Automation();
        testAutomation.setId(1L);
        testAutomation.setDisplayName("Test Automation");
        testAutomation.setScript("#!/bin/bash\necho 'test'");
        testAutomation.setType("bash");
        testAutomation.setUser(testUser);

        testSystem = HostSystem.builder()
            .id(1L)
            .displayName("Test System")
            .host("test-server.example.com")
            .sshUser("admin")
            .port(22)
            .build();
    }

    @Test
    void testAssignAutomationToSystem_Success() {
        when(scriptRepository.findById(1L)).thenReturn(Optional.of(testAutomation));
        when(systemRepository.findById(1L)).thenReturn(Optional.of(testSystem));
        when(assignmentRepository.findByAutomationIdAndSystemId(1L, 1L)).thenReturn(Optional.empty());

        AutomationAssignment expectedAssignment = new AutomationAssignment();
        expectedAssignment.setId(1L);
        expectedAssignment.setAutomation(testAutomation);
        expectedAssignment.setSystem(testSystem);
        expectedAssignment.setNumberExecs(0);

        when(assignmentRepository.save(any(AutomationAssignment.class))).thenReturn(expectedAssignment);

        AutomationAssignment result = service.assignAutomationToSystem(1L, 1L, null);

        assertNotNull(result);
        assertEquals(testAutomation, result.getAutomation());
        assertEquals(testSystem, result.getSystem());
        assertEquals(0, result.getNumberExecs());

        verify(assignmentRepository, times(1)).save(any(AutomationAssignment.class));
    }

    @Test
    void testAssignAutomationToSystem_WithCustomExecCount() {
        when(scriptRepository.findById(1L)).thenReturn(Optional.of(testAutomation));
        when(systemRepository.findById(1L)).thenReturn(Optional.of(testSystem));
        when(assignmentRepository.findByAutomationIdAndSystemId(1L, 1L)).thenReturn(Optional.empty());

        AutomationAssignment expectedAssignment = new AutomationAssignment();
        expectedAssignment.setId(1L);
        expectedAssignment.setAutomation(testAutomation);
        expectedAssignment.setSystem(testSystem);
        expectedAssignment.setNumberExecs(5);

        when(assignmentRepository.save(any(AutomationAssignment.class))).thenReturn(expectedAssignment);

        AutomationAssignment result = service.assignAutomationToSystem(1L, 1L, 5);

        assertNotNull(result);
        assertEquals(5, result.getNumberExecs());
    }

    @Test
    void testAssignAutomationToSystem_AlreadyExists() {
        AutomationAssignment existingAssignment = new AutomationAssignment();
        existingAssignment.setId(1L);
        existingAssignment.setAutomation(testAutomation);
        existingAssignment.setSystem(testSystem);
        existingAssignment.setNumberExecs(3);

        when(scriptRepository.findById(1L)).thenReturn(Optional.of(testAutomation));
        when(systemRepository.findById(1L)).thenReturn(Optional.of(testSystem));
        when(assignmentRepository.findByAutomationIdAndSystemId(1L, 1L)).thenReturn(Optional.of(existingAssignment));

        AutomationAssignment result = service.assignAutomationToSystem(1L, 1L, 0);

        assertNotNull(result);
        assertEquals(existingAssignment, result);

        verify(assignmentRepository, never()).save(any(AutomationAssignment.class));
    }

    @Test
    void testAssignAutomationToSystem_AutomationNotFound() {
        when(scriptRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            service.assignAutomationToSystem(999L, 1L, 0);
        });

        verify(assignmentRepository, never()).save(any(AutomationAssignment.class));
    }

    @Test
    void testAssignAutomationToSystem_SystemNotFound() {
        when(scriptRepository.findById(1L)).thenReturn(Optional.of(testAutomation));
        when(systemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            service.assignAutomationToSystem(1L, 999L, 0);
        });

        verify(assignmentRepository, never()).save(any(AutomationAssignment.class));
    }

    @Test
    void testUnassignAutomationFromSystem_Success() {
        AutomationAssignment assignment = new AutomationAssignment();
        assignment.setId(1L);
        assignment.setAutomation(testAutomation);
        assignment.setSystem(testSystem);

        when(assignmentRepository.findByAutomationIdAndSystemId(1L, 1L)).thenReturn(Optional.of(assignment));

        service.unassignAutomationFromSystem(1L, 1L);

        verify(assignmentRepository, times(1)).delete(assignment);
    }

    @Test
    void testUnassignAutomationFromSystem_NotFound() {
        when(assignmentRepository.findByAutomationIdAndSystemId(1L, 1L)).thenReturn(Optional.empty());

        service.unassignAutomationFromSystem(1L, 1L);

        verify(assignmentRepository, never()).delete(any(AutomationAssignment.class));
    }

    @Test
    void testGetAssignmentsForAutomation() {
        HostSystem system2 = HostSystem.builder()
            .id(2L)
            .displayName("Test System 2")
            .host("test-server-2.example.com")
            .build();

        AutomationAssignment assignment1 = new AutomationAssignment();
        assignment1.setId(1L);
        assignment1.setAutomation(testAutomation);
        assignment1.setSystem(testSystem);
        assignment1.setNumberExecs(5);

        AutomationAssignment assignment2 = new AutomationAssignment();
        assignment2.setId(2L);
        assignment2.setAutomation(testAutomation);
        assignment2.setSystem(system2);
        assignment2.setNumberExecs(3);

        when(assignmentRepository.findAllByAutomationId(1L)).thenReturn(Arrays.asList(assignment1, assignment2));

        List<AutomationAssignment> results = service.getAssignmentsForAutomation(1L);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(assignment1, results.get(0));
        assertEquals(assignment2, results.get(1));
    }

    @Test
    void testGetAssignmentsForSystem() {
        Automation automation2 = new Automation();
        automation2.setId(2L);
        automation2.setDisplayName("Test Automation 2");
        automation2.setUser(testUser);

        AutomationAssignment assignment1 = new AutomationAssignment();
        assignment1.setId(1L);
        assignment1.setAutomation(testAutomation);
        assignment1.setSystem(testSystem);

        AutomationAssignment assignment2 = new AutomationAssignment();
        assignment2.setId(2L);
        assignment2.setAutomation(automation2);
        assignment2.setSystem(testSystem);

        when(assignmentRepository.findAllBySystemId(1L)).thenReturn(Arrays.asList(assignment1, assignment2));

        List<AutomationAssignment> results = service.getAssignmentsForSystem(1L);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.contains(assignment1));
        assertTrue(results.contains(assignment2));
    }

    @Test
    void testDeleteAllAssignmentsForAutomation() {
        AutomationAssignment assignment1 = new AutomationAssignment();
        assignment1.setId(1L);
        AutomationAssignment assignment2 = new AutomationAssignment();
        assignment2.setId(2L);

        List<AutomationAssignment> assignments = Arrays.asList(assignment1, assignment2);

        when(assignmentRepository.findAllByAutomationId(1L)).thenReturn(assignments);

        service.deleteAllAssignmentsForAutomation(1L);

        verify(assignmentRepository, times(1)).findAllByAutomationId(1L);
        verify(assignmentRepository, times(1)).deleteAll(assignments);
    }

    @Test
    void testDeleteAllAssignmentsForAutomation_NoAssignments() {
        when(assignmentRepository.findAllByAutomationId(1L)).thenReturn(Arrays.asList());

        service.deleteAllAssignmentsForAutomation(1L);

        verify(assignmentRepository, times(1)).findAllByAutomationId(1L);
        verify(assignmentRepository, times(1)).deleteAll(anyList());
    }
}
