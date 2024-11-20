package io.dataguardians.sso.core.services.automation;

import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.automation.*;
import io.dataguardians.sso.core.repository.automation.ScriptAssignmentRepository;
import io.dataguardians.sso.core.repository.automation.ScriptCronEntryRepository;
import io.dataguardians.sso.core.repository.automation.ScriptExecutionRepository;
import io.dataguardians.sso.core.repository.automation.ScriptRepository;
import io.dataguardians.sso.core.repository.automation.ScriptShareRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AutomationService {

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private ScriptShareRepository scriptShareRepository;

    @Autowired
    private ScriptAssignmentRepository scriptAssignmentRepository;

    @Autowired
    private ScriptCronEntryRepository scriptCronEntryRepository;

    @Autowired
    private ScriptExecutionRepository scriptExecutionRepository;

    @Transactional
    public Automation addScript(Automation script) {
        return scriptRepository.save(script);
    }

    @Transactional(readOnly = true)
    public Optional<Automation> getScriptById(Long id) {
        return scriptRepository.findById(id);
    }

    @Transactional
    public void deleteScript(Long scriptId) {
        scriptRepository.deleteById(scriptId);
    }

    @Transactional
    public AutomationShare shareScript(Automation script, User user) {
        AutomationShare scriptShare = new AutomationShare();
        scriptShare.setAutomation(script);
        scriptShare.setUser(user);
        return scriptShareRepository.save(scriptShare);
    }

    @Transactional
    public AutomationAssignment assignScript(Automation script, HostSystem system, Integer numberExecs) {
        AutomationAssignment scriptAssignment = new AutomationAssignment();
        scriptAssignment.setAutomation(script);
        scriptAssignment.setSystem(system);
        scriptAssignment.setNumberExecs(numberExecs);
        return scriptAssignmentRepository.save(scriptAssignment);
    }

    @Transactional
    public AutomationCronEntry addCronEntry(Automation script, String cronExpression) {
        AutomationCronEntry cronEntry = new AutomationCronEntry();
        cronEntry.setAutomationId(script.getId());
        cronEntry.setScriptCron(cronExpression);
        return scriptCronEntryRepository.save(cronEntry);
    }

    @Transactional
    public AutomationExecution logScriptExecution(Automation script, HostSystem system, String executionOutput) {
        AutomationExecution scriptExecution = new AutomationExecution();
        scriptExecution.setAutomation(script);
        scriptExecution.setSystem(system);
        scriptExecution.setExecutionOutput(executionOutput);
        scriptExecution.setLogTm(new java.sql.Timestamp(System.currentTimeMillis()));
        return scriptExecutionRepository.save(scriptExecution);
    }
}