package io.sentrius.sso.core.services.automation;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.automation.Automation;
import io.sentrius.sso.core.model.automation.AutomationAssignment;
import io.sentrius.sso.core.repository.SystemRepository;
import io.sentrius.sso.core.repository.automation.ScriptAssignmentRepository;
import io.sentrius.sso.core.repository.automation.ScriptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing automation assignments to systems
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationAssignmentService {

    private final ScriptAssignmentRepository assignmentRepository;
    private final ScriptRepository scriptRepository;
    private final SystemRepository systemRepository;

    /**
     * Assign an automation to a system
     */
    @Transactional
    public AutomationAssignment assignAutomationToSystem(Long automationId, Long systemId, Integer numberExecs) {
        log.info("Assigning automation {} to system {}", automationId, systemId);
        
        Automation automation = scriptRepository.findById(automationId)
            .orElseThrow(() -> new IllegalArgumentException("Automation not found: " + automationId));
        
        HostSystem system = systemRepository.findById(systemId)
            .orElseThrow(() -> new IllegalArgumentException("System not found: " + systemId));
        
        Optional<AutomationAssignment> existingAssignment = 
            assignmentRepository.findByAutomationIdAndSystemId(automationId, systemId);
        
        if (existingAssignment.isPresent()) {
            log.warn("Assignment already exists for automation {} and system {}", automationId, systemId);
            return existingAssignment.get();
        }
        
        AutomationAssignment assignment = new AutomationAssignment();
        assignment.setAutomation(automation);
        assignment.setSystem(system);
        assignment.setNumberExecs(numberExecs != null ? numberExecs : 0);
        
        return assignmentRepository.save(assignment);
    }

    /**
     * Unassign an automation from a system
     */
    @Transactional
    public void unassignAutomationFromSystem(Long automationId, Long systemId) {
        log.info("Unassigning automation {} from system {}", automationId, systemId);
        
        Optional<AutomationAssignment> assignment = 
            assignmentRepository.findByAutomationIdAndSystemId(automationId, systemId);
        
        if (assignment.isPresent()) {
            assignmentRepository.delete(assignment.get());
        } else {
            log.warn("No assignment found for automation {} and system {}", automationId, systemId);
        }
    }

    /**
     * Get all assignments for an automation
     */
    @Transactional(readOnly = true)
    public List<AutomationAssignment> getAssignmentsForAutomation(Long automationId) {
        return assignmentRepository.findAllByAutomationId(automationId);
    }

    /**
     * Get all assignments for a system
     */
    @Transactional(readOnly = true)
    public List<AutomationAssignment> getAssignmentsForSystem(Long systemId) {
        return assignmentRepository.findAllBySystemId(systemId);
    }

    /**
     * Delete all assignments for an automation
     */
    @Transactional
    public void deleteAllAssignmentsForAutomation(Long automationId) {
        log.info("Deleting all assignments for automation {}", automationId);
        List<AutomationAssignment> assignments = assignmentRepository.findAllByAutomationId(automationId);
        assignmentRepository.deleteAll(assignments);
    }
}
