package io.sentrius.sso.core.services.automation;

import com.jcraft.jsch.*;
import io.sentrius.sso.core.model.HostSystem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Service for transferring files to remote systems via SCP
 */
@Slf4j
@Service
public class FileTransferService {

    private static final int TIMEOUT = 30000;
    private static final int DEFAULT_FILE_MODE = 0755;

    /**
     * Transfer a script to a remote system via SCP
     * 
     * @param system Target system
     * @param scriptContent Content of the script
     * @param remoteFilePath Remote path where the script should be saved
     * @return Map with transfer result
     */
    public Map<String, Object> transferScriptToSystem(HostSystem system, String scriptContent, String remoteFilePath) {
        log.info("Transferring script to system {} at path {}", system.getDisplayName(), remoteFilePath);
        
        Map<String, Object> result = new HashMap<>();
        
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp sftpChannel = null;
        
        try {
            session = createSession(jsch, system);
            session.connect(TIMEOUT);
            
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(TIMEOUT);
            
            byte[] scriptBytes = scriptContent.getBytes(StandardCharsets.UTF_8);
            
            try (InputStream inputStream = new ByteArrayInputStream(scriptBytes)) {
                sftpChannel.put(inputStream, remoteFilePath);
            }
            
            sftpChannel.chmod(DEFAULT_FILE_MODE, remoteFilePath);
            
            result.put("status", "success");
            result.put("message", "Script transferred successfully");
            result.put("remotePath", remoteFilePath);
            result.put("fileSize", scriptBytes.length);
            
            log.info("Successfully transferred script to {} ({} bytes)", remoteFilePath, scriptBytes.length);
            
        } catch (JSchException e) {
            log.error("SSH connection error while transferring script to {}", system.getDisplayName(), e);
            result.put("status", "error");
            result.put("message", "SSH connection failed: " + e.getMessage());
        } catch (SftpException e) {
            log.error("SFTP error while transferring script to {}", system.getDisplayName(), e);
            result.put("status", "error");
            result.put("message", "File transfer failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while transferring script to {}", system.getDisplayName(), e);
            result.put("status", "error");
            result.put("message", "Transfer failed: " + e.getMessage());
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return result;
    }

    /**
     * Transfer script using traditional SCP protocol (fallback method)
     */
    public Map<String, Object> transferScriptViaScp(HostSystem system, String scriptContent, String remoteFilePath) {
        log.info("Transferring script via SCP to system {} at path {}", system.getDisplayName(), remoteFilePath);
        
        Map<String, Object> result = new HashMap<>();
        
        JSch jsch = new JSch();
        Session session = null;
        
        try {
            session = createSession(jsch, system);
            session.connect(TIMEOUT);
            
            boolean ptimestamp = true;
            String command = "scp " + (ptimestamp ? "-p" : "") + " -t " + remoteFilePath;
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            
            OutputStream out = channel.getOutputStream();
            InputStream in = channel.getInputStream();
            
            channel.connect();
            
            if (checkAck(in) != 0) {
                throw new IOException("SCP command failed");
            }
            
            byte[] scriptBytes = scriptContent.getBytes(StandardCharsets.UTF_8);
            long fileSize = scriptBytes.length;
            
            if (ptimestamp) {
                command = "T" + (System.currentTimeMillis() / 1000) + " 0";
                command += (" " + (System.currentTimeMillis() / 1000) + " 0\n");
                out.write(command.getBytes());
                out.flush();
                if (checkAck(in) != 0) {
                    throw new IOException("SCP timestamp command failed");
                }
            }
            
            String filename = remoteFilePath.substring(remoteFilePath.lastIndexOf('/') + 1);
            command = "C0755 " + fileSize + " " + filename + "\n";
            out.write(command.getBytes());
            out.flush();
            if (checkAck(in) != 0) {
                throw new IOException("SCP file header command failed");
            }
            
            out.write(scriptBytes);
            out.write(0);
            out.flush();
            if (checkAck(in) != 0) {
                throw new IOException("SCP file content transfer failed");
            }
            
            out.close();
            channel.disconnect();
            
            result.put("status", "success");
            result.put("message", "Script transferred successfully via SCP");
            result.put("remotePath", remoteFilePath);
            result.put("fileSize", fileSize);
            
            log.info("Successfully transferred script via SCP to {} ({} bytes)", remoteFilePath, fileSize);
            
        } catch (Exception e) {
            log.error("Error transferring script via SCP to {}", system.getDisplayName(), e);
            result.put("status", "error");
            result.put("message", "SCP transfer failed: " + e.getMessage());
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        
        return result;
    }

    /**
     * Create an SSH session for the given system
     */
    private Session createSession(JSch jsch, HostSystem system) throws JSchException {
        Session session = jsch.getSession(
            system.getSshUser(),
            system.getHost(),
            system.getPort()
        );
        
        if (system.getSshPassword() != null && !system.getSshPassword().isEmpty()) {
            session.setPassword(system.getSshPassword());
        }
        
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        
        return session;
    }

    /**
     * Check acknowledgment from SCP
     */
    private int checkAck(InputStream in) throws IOException {
        int b = in.read();
        if (b == 0) return b;
        if (b == -1) return b;

        if (b == 1 || b == 2) {
            StringBuilder sb = new StringBuilder();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            } while (c != '\n');
            
            if (b == 1) {
                log.warn("SCP warning: {}", sb);
            }
            if (b == 2) {
                log.error("SCP error: {}", sb);
            }
        }
        return b;
    }
}
