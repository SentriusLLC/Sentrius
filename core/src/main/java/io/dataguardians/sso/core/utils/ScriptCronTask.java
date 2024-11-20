package io.dataguardians.sso.core.utils;


import java.util.List;
import io.dataguardians.sso.core.services.TerminalService;
import io.dataguardians.sso.core.services.UserService;
import io.dataguardians.sso.core.services.auditing.AuditService;
import io.dataguardians.sso.core.services.automation.AutomationService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ScriptCronTask implements Job {

    @Autowired
    private AutomationService automationService;

    @Autowired
    private UserService userService;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private AuditService sessionAuditService;



    /**
     * Called by the <code>{@link org.quartz.Scheduler}</code> when a <code>{@link org.quartz.Trigger}
     * </code> fires that is associated with the <code>Job</code>.
     *
     * @throws JobExecutionException if there is an exception while executing the job.
     */

    public void execute(JobExecutionContext context) throws JobExecutionException {

        var script_id = (Long) context.getJobDetail().getJobDataMap().get("script_id");
        var userId = (Long) context.getJobDetail().getJobDataMap().get("user_id");
        var scriptStr = (String) context.getJobDetail().getJobDataMap().get("script");
        var scriptType = (String) context.getJobDetail().getJobDataMap().get("script_type");
        var systemIds = (List<Long>) context.getJobDetail().getJobDataMap().get("system_ids");

  //      executeTask(script_id, userId, scriptType, scriptStr, systemIds);
    }
/*
    public String executeTask(
        Long scriptId,
        Long userId,
        String scriptStr,
        Long systemId,
        WatchDog watchdog,
        Map<String, String> environmentVariables,
        boolean appendToWatchDog) {
        try {
            return executeTask(
                PrivateKeyDB.getApplicationKey(),
                scriptId,
                userId,
                scriptStr,
                systemId,
                watchdog,
                environmentVariables,
                appendToWatchDog);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public String executeTask(
        ApplicationKey appKey,
        HostSystem hostSystem,
        String scriptStr,
        Map<String, String> environmentVariables,
        boolean appendToWatchDog) {
        Automation script = new Automation();
        script.setScript(scriptStr);
        script.setId(0L);
        String output = "";
        User user = null;
        try {

            output =
                terminalService.openTerminalForScript(
                    appKey,
                    "",
                    "",
                    0L,
                    0L,
                    hostSystem,
                    script,
                    null,
                    environmentVariables,
                    appendToWatchDog);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    public String executeTask(
        ApplicationKey appKey,
        HostSystem hostSystem,
        String scriptStr,
        Map<String, String> environmentVariables,
        boolean appendToWatchDog,
        boolean setPtyFalse) {
        Automation script = new Automation();
        script.setScript(scriptStr);
        script.setId(0L);
        String output = "";
        User user = null;
        try {

            output =
                terminalService.openTerminalForScript(
                    appKey,
                    "",
                    "",
                    0L,
                    0L,
                    hostSystem,
                    script,
                    null,
                    environmentVariables,
                    appendToWatchDog,
                    setPtyFalse);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    public String executeTask(
        ApplicationKey appKey,
        Long scriptId,
        Long userId,
        String scriptStr,
        Long systemId,
        WatchDog watchdog,
        Map<String, String> environmentVariables,
        boolean appendToWatchDog) {
        Automation script = new Automation();
        script.setScript(scriptStr);
        script.setId(scriptId);
        String output = "";
        User user = null;
        try {
            user = userService.getUser(userId);

            Long sessionId = SessionAuditDB.createSessionLog(user);

            HostSystem hostSystem = SystemDB.getSystem(systemId);

            if (script != null && script.getId() != null && script.getId() > 0) {

                output =
                    terminalService.openTerminalForScript(
                        appKey,
                        "",
                        "",
                        userId,
                        sessionId,
                        hostSystem,
                        script,
                        watchdog,
                        environmentVariables,
                        appendToWatchDog);

                if (null != output){
                    var scriptName = script.getDisplayNm();
                    if (null == scriptName){
                        scriptName = "Script";
                    }
                    var host = hostSystem.getHost();
                    if (null == host){
                        host = "Host";
                    }
                    SessionOutput sessionOutput =
                        SessionOutput.builder().host(host).displayNm(scriptName).sessionId(sessionId).instanceId(0).output(new StringBuilder(output)).build();
                    SessionAuditDB.insertTerminalLog(sessionOutput);
                }
                SessionOutputUtil.removeUserSession(sessionId);
                SessionAuditDB.closeSessionLog(sessionId);


                SessionOutputUtil.removeUserSession(sessionId);

                if (output != null && null == watchdog) {

                    ScriptDB.addOutput(script.getId(), systemId, output);
                }
            }

            // SessionAuditDB.se

        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    public static void executeTask(
        Long scriptId, Long userId, String scriptType, String scriptStr, List<Long> systemIds) {
        Automation script = new Automation();
        script.setScript(scriptStr);
        script.setType(scriptType);
        script.setId(scriptId);

        User user = null;
        try {
            user = UserDB.getUser(userId);

            for (Long systemId : systemIds) {

                Long sessionId = SessionAuditDB.createSessionLog(user);

                HostSystem hostSystem = SystemDB.getSystem(systemId);

                if (script != null && script.getId() != null && script.getId() > 0) {

                    if ("script".equals(script.getType())) {
                        String output =
                            SSHUtil.openTerminalForScript("", "", userId, sessionId, hostSystem, script);
                        var scriptName = script.getDisplayNm();
                        if (null == scriptName){
                            scriptName = "Script";
                        }
                        var host = hostSystem.getHost();
                        if (null == host){
                            host = "Host";
                        }
                        SessionOutput sessionOutput =
                            SessionOutput.builder().host(host).displayNm(scriptName).sessionId(sessionId).instanceId(0).output(new StringBuilder(output)).build();
                        SessionAuditDB.insertTerminalLog(sessionOutput);
                        SessionOutputUtil.removeUserSession(sessionId);
                        SessionAuditDB.closeSessionLog(sessionId);

                        if (output != null) {
                            ScriptDB.addOutput(script.getId(), systemId, output);
                        }
                    } else {
                        var automationTracker = AutomationTracker.getInstance();
                        Properties props = new Properties();
                        props.load(
                            new ByteArrayInputStream(script.getScript().getBytes(StandardCharsets.UTF_8)));

                        var newInstance =
                            PluginFactory.createNewInstance((String property) -> AppConfig.getProperty(property), script.getType(),
                                script.getScript());

                        List<Host> hostSystems = new ArrayList<>();
                        hostSystems.add(hostSystem);

                        List<SideEffect> sideEffects = new ArrayList<>();

                        var identifier = AutomationTracker.computeIdentifier(userId, script.getId());
                        final Automota tracker =
                            Automota.builder()
                                .asUser(userId)
                                .systems(hostSystems)
                                .databaseId(script.getId())
                                .instanceIdentifier(identifier)
                                .sideEffects(sideEffects)
                                .build();
                        automationTracker.execute(tracker, newInstance);

                        // should we notify the user?
                    }
                }
            }

        } catch (Exception ex) {
            log.error(ex.toString(), ex);
            throw new RuntimeException(ex);
        }
    }*/
}
