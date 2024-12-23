package io.sentrius.sso.core.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ConfigRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.sentrius.sso.automation.watchdog.WatchDog;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ApplicationKey;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.automation.Automation;
import io.sentrius.sso.core.model.automation.AutomationExecution;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.model.sessions.SessionOutput;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.ApplicationKeyRepository;
import io.sentrius.sso.core.repository.AutomationExecutionRepository;
import io.sentrius.sso.core.repository.SystemRepository;
import io.sentrius.sso.core.security.service.KeyStoreService;
import io.sentrius.sso.core.services.automation.AutomationService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import io.sentrius.sso.core.utils.SecureShellTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

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

    protected final AutomationExecutionRepository automationExecutionRepository;

    public static final int SESSION_TIMEOUT = 60000;
    public static final int CHANNEL_TIMEOUT = 60000;



    public static final String PRIVATE_KEY = "privateKey";
    public static final String PUBLIC_KEY = "publicKey";
    private final SessionTrackingService sessionTrackingService;
    private final SystemRepository systemRepository;

    public String openTerminalForScript(
        String passphrase,
        String password,
        User user,
        SessionLog sessionLog,
        HostGroup enclave,
        HostSystem selectedSystem,
        Automation scriptToRun)
        throws SQLException, GeneralSecurityException, JSchException, IOException {
        return openTerminalForScript(
            passphrase, password, user, sessionLog,enclave,selectedSystem, scriptToRun, null, null, false);
    }

    public String openTerminalForScript(
        String passphrase,
        String password,
        User user,
        SessionLog sessionLog,
        HostGroup enclave,
        HostSystem selectedSystem,
        Automation scriptToRun,
        WatchDog watchdog,
        Map<String, String> environmentVariables,
        boolean appendToWatchDog)
        throws SQLException, GeneralSecurityException, JSchException, IOException {
        ApplicationKey appKey = keyStoreService.getGlobalKey();
        return openTerminalForScript(
            appKey,
            passphrase,
            password,
            user,
            sessionLog,
            enclave,
            selectedSystem,
            scriptToRun,
            watchdog,
            environmentVariables,
            appendToWatchDog);
    }

    public String openTerminalForScript(
        ApplicationKey appKey,
        String passphrase,
        String password,
        User user,
        SessionLog sessionLog,
        HostGroup enclave,
        HostSystem selectedSystem,
        Automation scriptToRun,
        WatchDog watchdog,
        Map<String, String> environmentVariables,
        boolean appendToWatchDog)
        throws SQLException, GeneralSecurityException {
        return openTerminalForScript(
            appKey,
            passphrase,
            password,
            user,
            sessionLog,
            enclave,
            selectedSystem,
            scriptToRun,
            watchdog,
            environmentVariables,
            appendToWatchDog,
            false);
    }

    public String openTerminalForScript(
        ApplicationKey appKey,
        String passphrase,
        String password,
        User user,
        SessionLog sessionLog,
        HostGroup enclave,
        HostSystem selectedSystem,
        Automation scriptToRun,
        WatchDog watchdog,
        Map<String, String> environmentVariables,
        boolean appendToWatchDog,
        boolean setPtyFalse)
        throws SQLException, GeneralSecurityException {
        JSch jsch = new JSch();
        jsch.setInstanceLogger(
            new com.jcraft.jsch.Logger() {
                @Override
                public boolean isEnabled(int level) {
                    return true;
                }

                @Override
                public void log(int level, String message) {
                    log.info("JSCH: " + message);
                }
            });

        final var schSession =
            ConnectedSystem.builder().hostSystem(selectedSystem).enclave(enclave).user(user).session(sessionLog).build();

        selectedSystem.setStatusCd(HostSystem.SUCCESS_STATUS);

        String result = "";

        try {

            // check to see if passphrase has been provided
            if (passphrase == null || passphrase.trim().equals("")) {
                passphrase = appKey.getPassphrase();
                // check for null inorder to use key without passphrase
                if (passphrase == null) {
                    passphrase = "";
                }
            }
            String hostname = UUID.randomUUID().toString();
            Session session = null;
            if (!appKey.isFile()) {
                // add private key
                jsch.addIdentity(
                    appKey.getId().toString(),
                    appKey.getPrivateKey().trim().getBytes(),
                    appKey.getPublicKey().getBytes(),
                    passphrase.getBytes());
                session = jsch.getSession(selectedSystem.getSshUser(), selectedSystem.getHost(), selectedSystem.getPort());
            } else {
                // provide path to private key
                //    jsch.addIdentity(appKey.getPrivateKey().trim(),appKey.getPrivateKey() +".pub",
                // passphrase.getBytes());
                jsch.addIdentity(appKey.getPrivateKey().trim(), passphrase.getBytes());

                String config =
                    "Port 22\n"
                        + "\n"
                        + "Host "
                        + hostname
                        + "\n"
                        + "  User "
                        + selectedSystem.getSshUser()
                        + "\n"
                        + "  Hostname "
                        + selectedSystem.getHost()
                        + "\n"
                        + "Host *\n"
                        + "  ConnectTime "
                        + 60000
                        + "\n"
                        + "  PreferredAuthentications keyboard-interactive,password,publickey\n"
                        + "  IdentityFile "
                        + appKey.getPrivateKey()
                        + "\n"
                        + "  UserKnownHostsFile "
                        + knownHostService.getKnownHostsPath();

                ConfigRepository configRepository = com.jcraft.jsch.OpenSSHConfig.parse(config);

                jsch.setConfigRepository(configRepository);

                session = jsch.getSession(hostname);
            }
            // create session
            // Session session = jsch.getSession("foo");

            // session.setPassword(appKey.getPassphrase());
            // set password if it exists

            if (password != null && !password.trim().equals("")) {
                session.setPassword(password);
            }

            if (!appKey.isFile()) {
                jsch.setKnownHosts(knownHostService.getKnownHostsPath());
                session.setConfig("PreferredAuthentications", "publickey,password");
            }
            session.setServerAliveInterval(60000);
            try {
                session.connect(SESSION_TIMEOUT);
            } catch (JSchException je) {
                je.printStackTrace();
        /*
        if (je.getMessage().contains("USERAUTH fail")){
            if (!StringUtils.isNotEmpty(appKey.getPassphrase())){
                var cons = System.console();
                char[] passwd;
                if (cons != null &&
                        (passwd = cons.readPassword("[%s]", "Password:")) != null) {
                    appKey.setPassphrase(new String(passwd));
                    java.util.Arrays.fill(passwd, ' ');
                }
                jsch.removeAllIdentity();
                jsch.addIdentity(appKey.getPrivateKey().trim(), appKey.getPassphrase().getBytes));
                session.connect(SESSION_TIMEOUT);
            }
        } else {

         */
                throw je;
                // }
            }
            // session.connect(SESSION_TIMEOUT);
            Channel channel = session.openChannel("exec");

            // 80, 24, 640, 480);
            // ((ChannelShell) channel).setPtyType("xterm-256color", 425, 81, 0, 0);
            if (setPtyFalse) {
                ((ChannelExec) channel).setPty(false);
            } else {
                ((ChannelExec) channel).setPtyType("xterm-256color", 120, 81, 0, 0);
                channel.setInputStream(null);
                ((ChannelExec) channel).setPty(true);
            }

            // channel.setOutputStream(System.out);

            // FileOutputStream fos=new FileOutputStream("/tmp/stderr");
            // ((ChannelExec)channel).setErrStream(fos);

            String prepend = "";
            if (null != environmentVariables) {
                for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
                    prepend += "export " + entry.getKey() + "=" + entry.getValue() + " && ";
                }
            }

            var scr = scriptToRun.getScript().trim();
            scr = prepend + scr;
            scr = scr.replace('\r', '\n');

            ((ChannelExec) channel).setCommand(scr);

            OutputStream out = new ByteArrayOutputStream();
            channel.setOutputStream(out);
            ((ChannelExec) channel).setErrStream(out);

            InputStream outFromChannel = channel.getInputStream();

            channel.connect();

            byte[] buff = new byte[1024];
            int read;
            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            while (true) {
                if (null != watchdog && !watchdog.canRun()) {
                    break;
                }
                if (outFromChannel.available() > 0) {

                    read = outFromChannel.read(buff, 0, 1024);
                    if (read != -1) {
                        outputBuffer.write(buff, 0, read);
                        if (null != watchdog && appendToWatchDog) {
                            var st = new String(buff, 0, read);
                            watchdog.appendScriptOutput(outputToAppend -> {
                                automationExecutionRepository.save(
                                    AutomationExecution.builder().system(selectedSystem).automation(scriptToRun).executionOutput(
                                        outputToAppend.getOutput()).build());
                            }, st);
                        }
                        // sleep 100 ms. these are expected to be asynchronous
                    }
                }
                if (channel.isClosed()) {
                    if (outFromChannel.available() <= 0) break;
                }
                Thread.sleep(500);
            }

            var intermediate = new String(outputBuffer.toByteArray(), 0, outputBuffer.size());
            if (null != watchdog && appendToWatchDog) {
                watchdog.appendScriptOutput(outputToAppend -> {
                    automationExecutionRepository.save(
                        AutomationExecution.builder().system(selectedSystem).automation(scriptToRun).executionOutput(
                            outputToAppend.getOutput()).build());
                }, intermediate);
            }
            //                watchdog.
            result += intermediate;

            if (null != watchdog) {
                watchdog.appendError(out.toString());
            } else {
                result += out.toString();
            }
            outFromChannel.close();

            channel.disconnect();
            session.disconnect();

            // refresh keys for session

        } catch (Throwable ex) {
            ex.printStackTrace();
            log.info(ex.toString(), ex);
            selectedSystem.setErrorMsg(ex.getMessage());
            if (ex.getMessage().toLowerCase().contains("userauth fail")) {
                selectedSystem.setStatusCd(HostSystem.PUBLIC_KEY_FAIL_STATUS);
            } else if (ex.getMessage().toLowerCase().contains("auth fail")
                || ex.getMessage().toLowerCase().contains("auth cancel")) {
                selectedSystem.setStatusCd(HostSystem.AUTH_FAIL_STATUS);
            } else if (ex.getMessage().toLowerCase().contains("unknownhostexception")) {
                selectedSystem.setErrorMsg("DNS Lookup Failed");
                selectedSystem.setStatusCd(HostSystem.HOST_FAIL_STATUS);
            } else {
                selectedSystem.setStatusCd(HostSystem.GENERIC_FAIL_STATUS);
            }
        }

        //        SystemStatusDB.updateSystemStatus(hostSystem, userId);
        //      SystemDB.updateSystem(hostSystem);

        return result;
    }

}
