package io.dataguardians.sso.core.data.auditing;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import io.dataguardians.automation.auditing.PersistentMessage;
import io.dataguardians.automation.auditing.Recorder;
import io.dataguardians.automation.auditing.ShellAuditable;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.automation.auditing.TriggerAction;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.model.automation.Automation;
import io.dataguardians.sso.core.services.automation.AutomationService;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;

public class RecordingStudio extends Recorder {

    private final AutomationService automationService;
    private final SessionTrackingService sessionTrackingService;
    private final ConnectedSystem connectedSystem;
    List<String> commands = new ArrayList<>();

    public static final String RECORD = "record";
    public static final String STOP = "stop";

    boolean recordingStarted;

    String automationName;

    public RecordingStudio(ConnectedSystem session, SessionTrackingService sessionTrackingService, AutomationService service) {
        super(session.getUser(), session.getSession(), session.getHostSystem());
        // async thread evaluate
        recordingStarted = false;
        this.automationService = service;
        this.connectedSystem = session;
        this.sessionTrackingService = sessionTrackingService;
    }

    @Override
    protected void onPartial() {}

    @Override
    public void shutdown() {
        // nothing to
        // do here

    }

    public boolean isRecordingStarted() {
        return recordingStarted;
    }

    @Override
    public synchronized String clear(int keycode) {

        if (keycode == 13 && currentTrigger.getAction() == TriggerAction.DENY_ACTION) {
        } else {
            currentTrigger = Trigger.NO_ACTION;
        }
        return super.clear(keycode);
    }

    @Override
    protected synchronized TriggerAction submit(String cmd) {

        // currentTrigger
        String command = cmd.trim();
        if (recordingStarted && !command.startsWith(STOP)) {

            if (!cmd.isEmpty()) commands.add(command);
            return TriggerAction.NO_ACTION;
        } else {
            if (command.startsWith(RECORD)) {
                if (command.length() >= RECORD.length()) {
                    int firstIndex = command.indexOf(" ");
                    if (firstIndex >= RECORD.length()) {
                        automationName = command.substring(firstIndex + 1);
                    } else {
                        automationName = "";
                    }
                }
                if (automationName.isEmpty()) {
                    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    Date date = new Date();
                    automationName = "custom-" + dateFormat.format(date) + "-" + UUID.randomUUID().toString();
                }
                recordingStarted = true;
                sessionTrackingService.addPersistentMessage(connectedSystem, 
                    new PersistentMessage(
                        "Recording has commenced for "
                            + automationName
                            + ". Please type stop to end the session"));
                return TriggerAction.RECORD_ACTION;
            } else if (command.startsWith(STOP)) {
                recordingStarted = false;
                sessionTrackingService.addPersistentMessage(connectedSystem, new PersistentMessage(""));
                Automation script = new Automation();
                StringBuilder generatedScriptBuilder = new StringBuilder();
                generatedScriptBuilder.append(script.getScript());
                commands.forEach(line -> generatedScriptBuilder.append(line + "\n"));
                // = script.getScript();

                script.setAutomationName(automationName);

                script.setScript(generatedScriptBuilder.toString());
                automationName = "";
                commands.clear();
                automationService.addScript(script);
                return TriggerAction.RECORD_ACTION;
            }
        }

        return TriggerAction.NO_ACTION;
    }
}
