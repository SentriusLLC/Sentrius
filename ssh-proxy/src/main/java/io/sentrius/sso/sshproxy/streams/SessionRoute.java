package io.sentrius.sso.sshproxy.streams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.CryptoPrimitive;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.hostgroup.ProfileConfiguration;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.SshListenerService;
import io.sentrius.sso.core.services.TerminalService;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.sshproxy.handler.ResponseServiceSession;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;

@Slf4j
@Getter
@Builder
public final class SessionRoute {
    public final AtomicReference<ConnectedSystem> current = new AtomicReference<>();
    public final SwappableOutputStream out = new SwappableOutputStream(null); // to the SSH client
    final HostGroupService hostGroupService;
    final TerminalService terminalService;
    final SessionService sessionService;
    final CryptoService cryptoService;
    final SshListenerService sshListenerService;

    final TerminalSessionMetadataService terminalSessionMetadataService;


    // Track active sessions
    private static final ConcurrentMap<String, ConnectedSystem> activeSessions = new ConcurrentHashMap<>();



    public ConnectedSystem connect(User user, HostGroup hostGroup, InputStream in, Long hostId)
        throws IOException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException,
        InstantiationException, IllegalAccessException, SQLException, GeneralSecurityException {
        var hostSystem = getHostGroupService().getHostSystem(hostId);

        Hibernate.initialize(hostSystem.get().getPublicKeyList());

        ProfileConfiguration config = hostGroup.getConfiguration();

        var sessionLog = getSessionService().createSession(user.getName(), "", user.getUsername(),
            hostSystem.get().getHost());


        log.info("** Session rule size {}", config.getSessionRules().size());
        config.getSessionRules().forEach(
            rule -> {
                log.info("** Adding session rule: {}", rule.getSessionRuleClass());
            }

        );


        var sessionRules = getTerminalService().createRules(config);


        var connectedSystem = getTerminalService().openTerminal(user, sessionLog, hostGroup, "",
            hostSystem.get().getSshPassword(),
            hostSystem.get(),
            sessionRules);


        TerminalSessionMetadata sessionMetadata = TerminalSessionMetadata.builder().sessionStatus("ACTIVE")
            .hostSystem(hostSystem.get())
            .user(user)
            .startTime(new java.sql.Timestamp(System.currentTimeMillis()))
            .sessionLog(sessionLog)
            .build();

        sessionMetadata = getTerminalSessionMetadataService().createSession(sessionMetadata);

        activeSessions.put(hostGroup.getId().toString(), connectedSystem);

        var listenerThread = new ResponseServiceSession(connectedSystem, in, out);
        var encryptedSessionId = cryptoService.encrypt(connectedSystem.getSession().getId().toString());
        getSshListenerService().startListeningToSshServer(encryptedSessionId, listenerThread);

        current.set(connectedSystem);

        return connectedSystem;
    }

    public void set(ConnectedSystem next) { current.set(next); }

    public void setOutputStream(OutputStream next) { out.set(next); }

    public void cleanup(String sessionId){
        activeSessions.remove(sessionId);
    }
}