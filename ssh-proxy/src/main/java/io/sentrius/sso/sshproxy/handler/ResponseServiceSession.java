package io.sentrius.sso.sshproxy.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import io.sentrius.sso.core.integrations.ssh.DataSession;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.protobuf.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

@Slf4j
public class ResponseServiceSession implements DataSession {

    private final String sessionId;
    private final InputStream in;
    private final OutputStream out;
    private ConnectedSystem connectedSystem;

    public ResponseServiceSession(ConnectedSystem connectedSystem, InputStream in, OutputStream out) {
        this.sessionId = connectedSystem.getWebsocketSessionId();
        this.connectedSystem = connectedSystem;
        this.in = in;
        this.out = out;
    }
    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        log.info("Received message for session {}: {}", sessionId, message.getPayload());
        if (message instanceof TextMessage){
            byte[] messageBytes = Base64.getDecoder().decode(((TextMessage)message).getPayload());
            String terminalMessage = new String(messageBytes);

            Session.TerminalMessage auditLog =
                Session.TerminalMessage.parseFrom(messageBytes);

            log.info("Sending terminal message to session {}: {} {}", sessionId, terminalMessage,
                auditLog.getCommand());
            out.write(auditLog.getCommandBytes().toByteArray());
            out.flush();

        }
    }
}
