package io.sentrius.sso.core.model;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import io.sentrius.sso.automation.auditing.BaseAccessTokenAuditor;
import io.sentrius.sso.automation.auditing.SessionTokenEvaluator;
import io.sentrius.sso.core.data.auditing.RecordingStudio;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.users.User;
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



    private BaseAccessTokenAuditor terminalAuditor;

    @Builder.Default
    private List<SessionTokenEvaluator> sessionStartupActions = new ArrayList<>();

    // websocket for the terminal
    @Builder.Default
    private volatile String websocketSessionId = "";

    @Builder.Default
    private volatile String websocketChatSessionId = "";

    // websocket for a listener
    @Builder.Default
    private volatile String websocketListenerSessionId = "";

    RecordingStudio terminalRecorder;

    public Long getUserId() {
        return user.getId();
    }

    public int hashCode() {
        return session.getId().hashCode();
    }
}
