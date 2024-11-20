package io.dataguardians.sso.core.model;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import io.dataguardians.automation.auditing.BaseAuditor;
import io.dataguardians.automation.auditing.SessionRuleIfc;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.sso.core.data.auditing.RecordingStudio;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.model.sessions.SessionLog;
import io.dataguardians.sso.core.model.users.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedSystem {
    private HostGroup enclave;
    private User user;
    private SessionLog session;


    private Session jschSession;
    private UserInfo userinfo;
    private Channel channel;
    private PrintStream commander;
    private InputStream outFromChannel;
    private OutputStream inputToChannel;
    private HostSystem hostSystem;

    private BaseAuditor terminalAuditor;

    @Builder.Default
    private List<SessionRuleIfc> sessionStartupActions = new ArrayList<>();

    @Builder.Default
    private String websocketSessionId = "";

    RecordingStudio terminalRecorder;

    public Long getUserId() {
        return user.getId();
    }

    public int hashCode() {
        return session.getId().hashCode();
    }
}
