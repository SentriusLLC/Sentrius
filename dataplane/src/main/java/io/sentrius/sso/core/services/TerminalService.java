package io.sentrius.sso.core.services;

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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.sentrius.sso.automation.auditing.AccessTokenAuditor;
import io.sentrius.sso.automation.auditing.AccessTokenEvaluator;
import io.sentrius.sso.automation.auditing.RuleFactory;
import io.sentrius.sso.automation.auditing.SessionTokenEvaluator;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.automation.auditing.rules.SudoPrevention;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.data.SchUserInfo;
import io.sentrius.sso.core.data.auditing.RecordingStudio;
import io.sentrius.sso.core.model.ApplicationKey;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.hostgroup.ProfileConfiguration;
import io.sentrius.sso.core.model.hostgroup.ProfileRule;
import io.sentrius.sso.core.model.security.SessionRule;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.SessionOutput;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.users.UserPublicKey;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.repository.ApplicationKeyRepository;
import io.sentrius.sso.core.repository.SystemRepository;
import io.sentrius.sso.core.services.automation.AutomationService;
import io.sentrius.sso.core.services.security.KeyStoreService;
import io.sentrius.sso.core.services.security.ZeroTrustAccessTokenService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.utils.SecureShellTask;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
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
    protected final ZeroTrustAccessTokenService ztatService;
    protected final ApplicationContext applicationContext;
    protected final KnownHostService knownHostService;

    public static final int SESSION_TIMEOUT = 60000;
    public static final int CHANNEL_TIMEOUT = 60000;



    public static final String PRIVATE_KEY = "privateKey";
    public static final String PUBLIC_KEY = "publicKey";
    private final SessionTrackingService sessionTrackingService;
    private final SystemRepository systemRepository;

    public ConnectedSystem openTerminal(
        User user,
        SessionLog sessionLog,
        HostGroup enclave,
        String passphrase,
        String password,
        HostSystem selectedSystem, List<SessionTokenEvaluator> sessionRules
    ) throws SQLException, GeneralSecurityException {
        return openTerminal(user, sessionLog, enclave, passphrase, password, selectedSystem, sessionRules, false);
    }


    public ConnectedSystem openTerminal(
        User user,
        SessionLog sessionLog,
        HostGroup enclave,
        String passphrase,
        String password,
        HostSystem selectedSystem, List<SessionTokenEvaluator> sessionRules, boolean fetchedHostKey
    )
        throws SQLException, GeneralSecurityException {

        Map<String, PluggableServices> servicesMap = applicationContext.getBeansOfType(PluggableServices.class);
        Map<String, PluggableServices> services = applicationContext.getBeansOfType(PluggableServices.class);
        for (Map.Entry<String, PluggableServices> entry : servicesMap.entrySet()) {
            String beanName = entry.getValue().getName();
            PluggableServices service = entry.getValue();
            if (service.isEnabled()) {
                services.put(beanName, service);
                log.info("Processing service with bean name: " + beanName);
                log.info("Service name: " + service.getName());
            }
        }


        JSch jsch = new JSch();

        final var schSession =
            ConnectedSystem.builder().hostSystem(selectedSystem).enclave(enclave).user(user).session(sessionLog).build();

        try {
            ApplicationKey appKey = keyStoreService.getGlobalKey();
            // check to see if passphrase has been provided
            if (passphrase == null || passphrase.trim().equals("")) {
                passphrase = appKey.getPassphrase();
                // check for null inorder to use key without passphrase
                if (passphrase == null) {
                    passphrase = "";
                }
            }

            if (null == selectedSystem.getStatusCd() ||
                !selectedSystem.getStatusCd().equals(HostSystem.SUCCESS_STATUS)) {
                log.info("System is not ready for connection: " + selectedSystem.getDisplayName());
                authorizePublicKey(selectedSystem, enclave, passphrase, password);
            }


            // add private key
            jsch.addIdentity(
                appKey.getId().toString(),
                appKey.getPrivateKey().trim().getBytes(),
                appKey.getPublicKey().getBytes(),
                passphrase.getBytes());

            // create session
            log.info("Connecting to system: " + schSession.getHostSystem().getDisplayName());
            Session session =
                jsch.getSession(selectedSystem.getSshUser(), schSession.getHostSystem().getHost(),
                    schSession.getHostSystem().getPort());
            SchUserInfo userInfo = SchUserInfo.builder().build();
            session.setUserInfo(userInfo);
            // set password if it exists
            if (password != null && !password.trim().equals("")) {
                session.setPassword(password);
            } else {
                log.info("Using public key for user {} ; {}", schSession.getUser().getUsername(),appKey.getPublicKey());
            }
            if (systemOptions.getTestMode()) {
                session.setConfig("StrictHostKeyChecking", "no");
            }
            Set<ProfileRule> rules = enclave.getRules();

            // rules that are in-line with data/command processing
            List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();
            // rules that are ONLY before a session begins.
            List<SessionTokenEvaluator> definedSessionRules = new ArrayList<>();

            RuleFactory.createRules(systemOptions, schSession, sessionTrackingService,
                rules.stream().collect(Collectors.toList()),
                synchronousRules, definedSessionRules, services);


            sessionRules.addAll(definedSessionRules);

            for(var rule : sessionRules){
                rule.setConnectedSystem(schSession);
                rule.setTrackingService(sessionTrackingService);
                var trg = rule.trigger("");
                    if (trg.get().getAction() == TriggerAction.DENY_ACTION){
                        return schSession;
                    }

            }

            schSession.setSessionStartupActions(sessionRules);

            var serverAliveInterval = systemOptions.getServerAliveInterval() * 1000;
            jsch.setKnownHosts(knownHostService.getKnownHostsPath());
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
            AccessTokenAuditor terminalAuditor =
                new AccessTokenAuditor(ztatService, schSession, sessionOutputService,
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



            for(ProfileRule rule : rules) {
                log.info("Apply8ing {} to system", rule.getRuleName());
            }
            if (null != enclave.getConfiguration() && !enclave.getConfiguration().getAllowSudo()) {
                terminalAuditor.addRule(new SudoPrevention());
            }
            terminalAuditor.setSynchronousRules(synchronousRules);
            schSession.setTerminalAuditor(terminalAuditor);

            schSession.setTerminalRecorder(recorder);

            // refresh keys for session
            addPubKey(user, enclave, selectedSystem, session, appKey.getPublicKey());
            schSession.getHostSystem().setStatusCd(HostSystem.SUCCESS_STATUS);
            // we've connected
            systemRepository.save(schSession.getHostSystem());
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
            } else if (ex.getMessage().toLowerCase().contains("reject hostkey")) {
                // host key has been changed.
                if (!fetchedHostKey) {
                    schSession.getHostSystem().setStatusCd(HostSystem.PUBLIC_KEY_FAIL_STATUS);
                    systemRepository.save(schSession.getHostSystem());
                    log.info("Failed to connect to system: " + schSession.getHostSystem().getDisplayName());
                    // remove the tracking
                    sessionOutputService.removeOutput(schSession);
                    //sessionService.closeSession(sessionLog);
                    ApplicationKey appKey = null;
                    try {
                        appKey = keyStoreService.getGlobalKey();
                    } catch (JSchException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    fetchHostKey(selectedSystem, appKey, passphrase, password);

                    return openTerminal(user, sessionLog, enclave, passphrase, password, selectedSystem,
                        sessionRules, true
                    );
                } else {
                    schSession.getHostSystem().setStatusCd(HostSystem.PUBLIC_KEY_FAIL_STATUS);
                }
            } else if (ex.getMessage().toLowerCase().contains("hostkey has been changed")) {
                if (enclave.getConfiguration().getAutoApproveChangingHostKey()) {
                    // we need to add the host key to known hosts
                    if (!fetchedHostKey) {
                        schSession.getHostSystem().setStatusCd(HostSystem.PUBLIC_KEY_FAIL_STATUS);
                        systemRepository.save(schSession.getHostSystem());
                        log.info("Failed to connect to system: " + schSession.getHostSystem().getDisplayName());
                        // remove the tracking
                        sessionOutputService.removeOutput(schSession);
                        //sessionService.closeSession(sessionLog);
                        ApplicationKey appKey = null;
                        try {
                            appKey = keyStoreService.getGlobalKey();
                        } catch (JSchException e) {
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        fetchHostKey(selectedSystem, appKey, passphrase, password);

                        return openTerminal(user, sessionLog, enclave, passphrase, password, selectedSystem,
                            sessionRules, true
                        );
                    } else {
                        log.info("Host key has changed and auto approve is not enabled 2");
                        schSession.getHostSystem().setStatusCd(HostSystem.PUBLIC_KEY_FAIL_STATUS);
                    }
                } else {
                    log.info("Host key has changed and auto approve is not enabled");
                    schSession.getHostSystem().setStatusCd(HostSystem.PUBLIC_KEY_FAIL_STATUS);
                }
            } else if (ex.getMessage().toLowerCase().contains("unknownhostexception")) {
                schSession.getHostSystem().setErrorMsg("DNS Lookup Failed");
                schSession.getHostSystem().setStatusCd(HostSystem.HOST_FAIL_STATUS);
            } else {
                schSession.getHostSystem().setStatusCd(HostSystem.GENERIC_FAIL_STATUS);
            }
        }

        // add session to map
        if (!schSession.getHostSystem().getStatusCd().equals(HostSystem.SUCCESS_STATUS)) {
            systemRepository.save(schSession.getHostSystem());
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

    protected void fetchHostKey(HostSystem hostSystem, ApplicationKey appKey, String passphrase, String password) {
        Session session = null;
        try{
                JSch jsch = new JSch();

                // Create a dummy session to fetch the host key
            // add private key
            jsch.addIdentity(
                appKey.getId().toString(),
                appKey.getPrivateKey().trim().getBytes(),
                appKey.getPublicKey().getBytes(),
                passphrase.getBytes());

            // create session

                session =
                    jsch.getSession(hostSystem.getSshUser(), hostSystem.getHost(),
                        hostSystem.getPort());
                SchUserInfo userInfo = SchUserInfo.builder().build();
                session.setUserInfo(userInfo);
                // set password if it exists
                if (password != null && !password.trim().equals("")) {
                    session.setPassword(password);
                }

                // Disable authentication since we're only fetching the host key
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("UserKnownHostsFile", "/dev/null");



                // Connect to fetch the host key
                session.connect(5000); // Timeout in milliseconds

                // Get the host key
                HostKey hostKey = session.getHostKey();
                log.info("Fetched Host Key:");
                log.info("Host: " + hostKey.getHost());
                log.info("Type: " + hostKey.getType());
                log.info("Key: " + hostKey.getKey());

                knownHostService.saveHostKey(hostSystem.getHost(), hostKey.getType(), hostKey.getKey());

                // Disconnect session

            } catch (JSchException e) {
                e.printStackTrace();
            } finally {
                if (session != null) {
                    session.disconnect();
                }
        }
    }

    /**
     * distributes authorized keys for host system
     *
     * @param hostSystem object contains host system information
     * @param passphrase ssh key passphrase
     * @param password password to host system if needed
     * @return status of key distribution
     */
    public HostSystem authorizePublicKey(
        HostSystem hostSystem,HostGroup enclave, String passphrase, String password) {

        JSch jsch = new JSch();
        Session session = null;
        hostSystem.setStatusCd(HostSystem.SUCCESS_STATUS);
        try {
            ApplicationKey appKey = keyStoreService.getGlobalKey();
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
            session = jsch.getSession(hostSystem.getSshUser(), hostSystem.getHost(), hostSystem.getPort());

            // set password if passed in
            if (password != null && !password.equals("")) {
                session.setPassword(password);
            }
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
            session.setServerAliveInterval(SESSION_TIMEOUT);
            session.connect(SESSION_TIMEOUT);

            addPubKey(null, enclave,hostSystem, session, appKey.getPublicKey());

        } catch (JSchException | GeneralSecurityException ex) {
            log.info(ex.toString(), ex);
            hostSystem.setErrorMsg(ex.getMessage());
            if (ex.getMessage().toLowerCase().contains("userauth fail")) {
                hostSystem.setStatusCd(HostSystem.PUBLIC_KEY_FAIL_STATUS);
            } else if (ex.getMessage().toLowerCase().contains("auth fail")
                || ex.getMessage().toLowerCase().contains("auth cancel")) {
                hostSystem.setStatusCd(HostSystem.AUTH_FAIL_STATUS);
            } else if (ex.getMessage().toLowerCase().contains("unknownhostexception")) {
                hostSystem.setErrorMsg("DNS Lookup Failed");
                hostSystem.setStatusCd(HostSystem.HOST_FAIL_STATUS);
            } else {
                hostSystem.setStatusCd(HostSystem.GENERIC_FAIL_STATUS);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (session != null) {
            session.disconnect();
        }

        return hostSystem;
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
                List<UserPublicKey> assignedKeys = null == user ? userPublicKeyService.getPublicKeysForHostGroup(
                    enclave.getId()) : userPublicKeyService.getPublicKeysForHostGroup(
                    user.getId(),
                    enclave.getId()
                );
                for (UserPublicKey pkey : assignedKeys) {
                    var key = pkey.getPublicKey();
                    newKeysBuilder.append(key.replace("\n", "").trim()).append("\n");
                }
                newKeysBuilder.append(existingKeys);
                newKeysBuilder.append(appPubKey);
            } else {
                if (existingKeys.indexOf(appPubKey) < 0) {
                    newKeysBuilder.append(existingKeys).append("\n").append(appPubKey);
                } else {
                    newKeysBuilder.append(existingKeys);
                }
            }

            String newKeys = newKeysBuilder.toString();
            log.info("Update Public Keys  ==> " + newKeys);
            if (!newKeys.equals(existingKeys)) {

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

    public List<SessionTokenEvaluator> createRules(ProfileConfiguration configuration)
        throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException,
        IllegalAccessException {
        List<SessionTokenEvaluator> rules = new ArrayList<>();
        for (SessionRule rule : configuration.getSessionRules()){
            var clazz = Class.forName(rule.getSessionRuleClass());
            var instance = clazz.asSubclass(SessionTokenEvaluator.class).getConstructor().newInstance();
            rules.add(instance);
        }
        return rules;
    }


}
