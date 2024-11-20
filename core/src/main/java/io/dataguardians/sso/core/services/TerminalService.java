package io.dataguardians.sso.core.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.dataguardians.automation.auditing.RuleAlertAuditor;
import io.dataguardians.automation.auditing.SessionRuleIfc;
import io.dataguardians.automation.auditing.TriggerAction;
import io.dataguardians.automation.auditing.rules.SudoPrevention;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.data.SchUserInfo;
import io.dataguardians.sso.core.data.auditing.RecordingStudio;
import io.dataguardians.sso.core.model.ApplicationKey;
import io.dataguardians.sso.core.model.ConnectedSystem;
import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.hostgroup.ProfileConfiguration;
import io.dataguardians.sso.core.model.hostgroup.ProfileRule;
import io.dataguardians.sso.core.model.security.SessionRule;
import io.dataguardians.sso.core.model.sessions.SessionLog;
import io.dataguardians.sso.core.model.sessions.SessionOutput;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.users.UserPublicKey;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import io.dataguardians.sso.core.repository.ApplicationKeyRepository;
import io.dataguardians.sso.core.security.service.KeyStoreService;
import io.dataguardians.sso.core.services.automation.AutomationService;
import io.dataguardians.sso.core.services.terminal.SessionTrackingService;
import io.dataguardians.sso.core.utils.SecureShellTask;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalService {

    protected final SystemOptions systemOptions;
    protected final SessionService sessionService;
    protected final ApplicationKeyRepository applicationKeyRepository;
    protected final SessionTrackingService sessionOutputService;
    protected final SecureShellTask secureShellTask;
    protected final AutomationService automationService;
    protected final UserPublicKeyService userPublicKeyService;
    protected final KeyStoreService keyStoreService;

    public static final int SESSION_TIMEOUT = 60000;
    public static final int CHANNEL_TIMEOUT = 60000;



    public static final String PRIVATE_KEY = "privateKey";
    public static final String PUBLIC_KEY = "publicKey";
    private final SessionTrackingService sessionTrackingService;


    public ConnectedSystem openSSHTermOnSystem(
        User user,
        SessionLog sessionLog,
        HostGroup enclave,
        String passphrase,
        String password,
        HostSystem selectedSystem, List<SessionRuleIfc> sessionRules
    )
        throws SQLException, GeneralSecurityException {
        JSch jsch = new JSch();

        final var schSession =
            ConnectedSystem.builder().hostSystem(selectedSystem).enclave(enclave).user(user).session(sessionLog).build();

        try {
            ApplicationKey appKey = keyStoreService.getApplicationKey();
            // check to see if passphrase has been provided
            if (passphrase == null || passphrase.trim().equals("")) {
                passphrase = appKey.getPassphrase();
                // check for null inorder to use key without passphrase
                if (passphrase == null) {
                    passphrase = "";
                }
            }

            // add private key
            jsch.addIdentity(
                appKey.getId().toString(),
                appKey.getPrivateKey().trim().getBytes(),
                appKey.getPublicKey().getBytes(),
                passphrase.getBytes());

            // create session
            log.info("Connecting to system: " + schSession.getHostSystem().getDisplayName());
            log.info("Using public key: " + appKey.getPublicKey());
            log.info("Using user: " + schSession.getUser().getUsername());
            log.info("Using password: " + password);
            Session session =
                jsch.getSession(selectedSystem.getSshUser(), schSession.getHostSystem().getHost(),
                    schSession.getHostSystem().getPort());
            SchUserInfo userInfo = SchUserInfo.builder().build();
            session.setUserInfo(userInfo);
            // set password if it exists
            if (password != null && !password.trim().equals("")) {
                session.setPassword(password);
            }
            if (systemOptions.getTestMode()) {
                session.setConfig("StrictHostKeyChecking", "no");
            }

            for(var rule : sessionRules){
                rule.setConnectedSystem(schSession);
                rule.setTrackingService(sessionTrackingService);
                var trg = rule.trigger("");
                    if (trg.getAction() == TriggerAction.DENY_ACTION){
                        return schSession;
                    }

            }

            var serverAliveInterval = systemOptions.getServerAliveInterval() * 1000;
            jsch.setKnownHosts(systemOptions.knownHostsPath);
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
            session.setServerAliveInterval(serverAliveInterval);
            session.connect(SESSION_TIMEOUT);
            Channel channel = session.openChannel("shell");
            if ("true".equals(systemOptions.getAgentForwarding())){
                ((ChannelShell) channel).setAgentForwarding(true);
            }

            // 80, 24, 640, 480);
            // ((ChannelShell) channel).setPtyType("xterm-256color", 425, 81, 0, 0);
            ((ChannelShell) channel).setPtyType("xterm-256color");

            InputStream outFromChannel = channel.getInputStream();
            RecordingStudio recorder = new RecordingStudio(schSession,sessionTrackingService, automationService);
            RuleAlertAuditor terminalAuditor =
                new RuleAlertAuditor(schSession, sessionOutputService,
                    recorder);

            schSession.setChannel(channel);
            schSession.setTerminalRecorder(recorder);
            schSession.setTerminalAuditor(terminalAuditor);
            // new session output
            SessionOutput sessionOutput = new SessionOutput(schSession);
            // need to add output here.  This is the first output
            sessionOutputService.addOutput(sessionOutput);
            var message = userInfo.getMessage();
            if (null != message ) {
                message = message.replaceAll("\n", "\r\n");
                sessionOutputService.addToOutput(
                    schSession, message.toCharArray(), 0,
                    message.length()
                );
                sessionOutputService.addToOutput(
                    schSession, message.toCharArray(), 0,
                    message.length()
                );
            }

            secureShellTask.execute(sessionOutput, outFromChannel);

            OutputStream inputToChannel = channel.getOutputStream();
            PrintStream commander = new PrintStream(inputToChannel, true);

            channel.connect();
            schSession.setOutFromChannel(outFromChannel);
            schSession.setInputToChannel(inputToChannel);
            schSession.setCommander(commander);

            Set<ProfileRule> rules = enclave.getRules();

            for(ProfileRule rule : rules) {
                log.info("Apply8ing {} to system", rule.getRuleName());
            }
            if (null != enclave.getConfiguration() && !enclave.getConfiguration().getAllowSudo()) {
                terminalAuditor.addRule(new SudoPrevention());
            }
            terminalAuditor.setSynchronousRules(rules.stream().collect(Collectors.toList()));
            schSession.setTerminalAuditor(terminalAuditor);

            schSession.setTerminalRecorder(recorder);

            // refresh keys for session
            addPubKey(user, enclave, selectedSystem, session, appKey.getPublicKey());
            schSession.getHostSystem().setStatusCd(HostSystem.SUCCESS_STATUS);
        } catch (JSchException
                 | IOException
                 | GeneralSecurityException
                 | ClassNotFoundException
                 | NoSuchMethodException
                 | InvocationTargetException
                 | InstantiationException
                 | IllegalAccessException ex) {
            ex.printStackTrace();
            log.info(ex.toString(), ex);
            schSession.getHostSystem().setErrorMsg(ex.getMessage());
            if (ex.getMessage().toLowerCase().contains("userauth fail")) {
                schSession.getHostSystem().setStatusCd(HostSystem.PUBLIC_KEY_FAIL_STATUS);
            } else if (ex.getMessage().toLowerCase().contains("auth fail")
                || ex.getMessage().toLowerCase().contains("auth cancel")) {
                ex.printStackTrace();
                schSession.getHostSystem().setStatusCd(HostSystem.AUTH_FAIL_STATUS);
            } else if (ex.getMessage().toLowerCase().contains("unknownhostexception")) {
                schSession.getHostSystem().setErrorMsg("DNS Lookup Failed");
                schSession.getHostSystem().setStatusCd(HostSystem.HOST_FAIL_STATUS);
            } else {
                schSession.getHostSystem().setStatusCd(HostSystem.GENERIC_FAIL_STATUS);
            }
        }

        // add session to map
        if (!schSession.getHostSystem().getStatusCd().equals(HostSystem.SUCCESS_STATUS)) {
            log.info("Failed to connect to system: " + schSession.getHostSystem().getDisplayName());
            // remove the tracking
            sessionOutputService.removeOutput(schSession);
            sessionService.closeSession(sessionLog);
           /*
            // get the server maps for user
            UserSchSessions userSchSessions = userSessionMap.get(sessionId);

            // if no user session create a new one
            if (userSchSessions == null) {
                userSchSessions = new UserSchSessions();
            }
            Map<Integer, SchSession> schSessionMap = userSchSessions.getSchSessionMap();

            // add server information
            schSessionMap.put(instanceId, schSession);
            userSchSessions.setSchSessionMap(schSessionMap);
            // add back to map
            userSessionMap.put(sessionId, userSchSessions);

            */
        } else {
            log.info("Connected to system: " + schSession.getHostSystem().getDisplayName());
            // add to tracking
        }

        //SystemStatusDB.updateSystemStatus(hostSystem, userId);
        //SystemDB.updateSystem(hostSystem);

        return schSession;
    }


    public  HostSystem addPubKey(User user, HostGroup enclave, HostSystem hostSystem, Session session,
                                 String appPublicKey) {

        try {
            String authorizedKeys = hostSystem.getAuthorizedKeys().replaceAll("~\\/|~", "");

            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand("cat " + authorizedKeys);
            ((ChannelExec) channel).setErrStream(System.err);
            channel.setInputStream(null);

            InputStream in = channel.getInputStream();
            InputStreamReader is = new InputStreamReader(in);
            BufferedReader reader = new BufferedReader(is);

            channel.connect(CHANNEL_TIMEOUT);

            String appPubKey = appPublicKey.replace("\n", "").trim();
            StringBuilder existingKeysBuilder = new StringBuilder();

            String currentKey;
            while ((currentKey = reader.readLine()) != null) {
                existingKeysBuilder.append(currentKey).append("\n");
            }
            String existingKeys = existingKeysBuilder.toString();
            existingKeys = existingKeys.replaceAll("\\n$", "");
            reader.close();
            // disconnect
            channel.disconnect();

            StringBuilder newKeysBuilder = new StringBuilder();
            if (systemOptions.getKeyManagementEnabled()) {
                // get keys assigned to system
                List<UserPublicKey> assignedKeys = userPublicKeyService.getPublicKeysForHostGroup(user.getId(),
                    enclave.getId());
                for (UserPublicKey pkey : assignedKeys) {
                    var key = pkey.getPublicKey();
                    newKeysBuilder.append(key.replace("\n", "").trim()).append("\n");
                }
                newKeysBuilder.append(appPubKey);
            } else {
                if (existingKeys.indexOf(appPubKey) < 0) {
                    newKeysBuilder.append(existingKeys).append("\n").append(appPubKey);
                } else {
                    newKeysBuilder.append(existingKeys);
                }
            }

            String newKeys = newKeysBuilder.toString();
            if (!newKeys.equals(existingKeys)) {
                log.info("Update Public Keys  ==> " + newKeys);
                channel = session.openChannel("exec");
                ((ChannelExec) channel)
                    .setCommand(
                        "echo '" + newKeys + "' > " + authorizedKeys + "; chmod 600 " + authorizedKeys);
                ((ChannelExec) channel).setErrStream(System.err);
                channel.setInputStream(null);
                channel.connect(CHANNEL_TIMEOUT);
                // disconnect
                channel.disconnect();
            }

        } catch (JSchException | IOException ex) {
            log.error(ex.toString(), ex);
        }
        return hostSystem;
    }

    @PreDestroy
    public void shutdown() {
        sessionOutputService.shutdown();
    }

    public List<SessionRuleIfc> createRules(ProfileConfiguration configuration)
        throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException,
        IllegalAccessException {
        List<SessionRuleIfc> rules = new ArrayList<>();
        for (SessionRule rule : configuration.getSessionRules()){
            var clazz = Class.forName(rule.getSessionRuleClass());
            var instance = clazz.asSubclass(SessionRuleIfc.class).getConstructor().newInstance();
            rules.add(instance);
        }
        return rules;
    }


}
