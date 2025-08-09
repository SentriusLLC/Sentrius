package io.sentrius.sso.core.integrations.ssh;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketSession;

public class DataWebSession implements DataSession, WebSocketSession {

    private final WebSocketSession webSocketSession;

    public DataWebSession(WebSocketSession webSocketSession) {
        this.webSocketSession = webSocketSession;
    }

    @Override
    public String getId() {
        return webSocketSession.getId();
    }

    @Override
    public URI getUri() {
        return webSocketSession.getUri();
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return webSocketSession.getHandshakeHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return webSocketSession.getAttributes();
    }

    @Override
    public Principal getPrincipal() {
        return webSocketSession.getPrincipal();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return webSocketSession.getLocalAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return webSocketSession.getRemoteAddress();
    }

    @Override
    public String getAcceptedProtocol() {
        return webSocketSession.getAcceptedProtocol();
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
        webSocketSession.setTextMessageSizeLimit(messageSizeLimit);
    }

    @Override
    public int getTextMessageSizeLimit() {
        return webSocketSession.getTextMessageSizeLimit();
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        webSocketSession.setBinaryMessageSizeLimit(messageSizeLimit);
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return webSocketSession.getBinaryMessageSizeLimit();
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return webSocketSession.getExtensions();
    }

    @Override
    public boolean isOpen() {
        return webSocketSession.isOpen();
    }

    @Override
    public void close() throws IOException {
        webSocketSession.close();
    }

    @Override
    public void close(CloseStatus status) throws IOException {
        webSocketSession.close(status);
    }

    // Delegate other WebSocketSession methods as needed
    // For example:
    @Override
    public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws java.io.IOException {
        webSocketSession.sendMessage(message);
    }

    // Add more methods as required by your application logic

}
