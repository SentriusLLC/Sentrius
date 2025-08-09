package io.sentrius.sso.core.integrations.ssh;

import java.io.IOException;
import org.springframework.web.socket.WebSocketMessage;

public interface DataSession {

    String getId();

    boolean isOpen();

    void sendMessage(WebSocketMessage<?> message) throws IOException;
}
