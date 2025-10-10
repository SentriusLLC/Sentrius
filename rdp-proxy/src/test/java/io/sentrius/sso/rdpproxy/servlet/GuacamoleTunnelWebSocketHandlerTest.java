package io.sentrius.sso.rdpproxy.servlet;

import io.sentrius.sso.rdpproxy.security.AsymmetricJwtService;
import io.sentrius.sso.rdpproxy.service.GuacamoleRdpService;
import io.sentrius.sso.rdpproxy.service.RdpCommandProcessor;
import io.sentrius.sso.rdpproxy.service.RdpScreenshotCaptureService;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.io.GuacamoleWriter;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test for GuacamoleTunnelWebSocketHandler with RdpCommandProcessor integration
 */
@ExtendWith(MockitoExtension.class)
class GuacamoleTunnelWebSocketHandlerTest {

    @Mock
    private GuacamoleRdpService guacamoleRdpService;

    @Mock
    private AsymmetricJwtService asymmetricJwtService;

    @Mock
    private RdpCommandProcessor rdpCommandProcessor;

    @Mock
    private UserService userService;

    @Mock
    private HostGroupService hostGroupService;

    @Mock
    private SessionTrackingService sessionTrackingService;

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private RdpScreenshotCaptureService screenshotCaptureService;

    @Mock
    private WebSocketSession webSocketSession;

    @Mock
    private GuacamoleTunnel tunnel;

    @Mock
    private GuacamoleWriter writer;

    private GuacamoleTunnelWebSocketHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new GuacamoleTunnelWebSocketHandler(
            guacamoleRdpService,
            asymmetricJwtService,
            rdpCommandProcessor,
            userService,
            hostGroupService,
            sessionTrackingService,
            systemOptions,
            applicationContext,
            screenshotCaptureService
        );

        // Setup default mocks with lenient to avoid unnecessary stubbing errors
        lenient().when(webSocketSession.getId()).thenReturn("test-session-id");
        lenient().when(webSocketSession.isOpen()).thenReturn(true);
        lenient().when(tunnel.getUUID()).thenReturn(UUID.randomUUID());
        lenient().when(tunnel.acquireWriter()).thenReturn(writer);
    }

    @Test
    void testHandleTextMessage_KeyboardInput_Allowed() throws Exception {
        // Arrange - Setup connection first
        URI uri = new URI("ws://localhost/guacamole/tunnel?token=test-jwt-token");
        when(webSocketSession.getUri()).thenReturn(uri);
        when(guacamoleRdpService.createRdpTunnel(anyString())).thenReturn(tunnel);
        
        // Establish connection
        handler.afterConnectionEstablished(webSocketSession);
        
        // Setup for keyboard input processing
        when(rdpCommandProcessor.processInputEvent(any(ConnectedSystem.class), 
            any(RdpCommandProcessor.RdpInputEvent.class), 
            any(ByteArrayOutputStream.class))).thenReturn(true); // Allow input
        
        // Guacamole keyboard instruction: 3.key,5.65307,1.1;
        // This represents a key press with keysym 65307 (Escape key)
        String keyInstruction = "3.key,5.65307,1.1;";
        TextMessage message = new TextMessage(keyInstruction);
        
        // Act
        handler.handleTextMessage(webSocketSession, message);
        
        // Assert
        // Verify that RdpCommandProcessor was called to analyze the input
        verify(rdpCommandProcessor).processInputEvent(
            any(ConnectedSystem.class),
            argThat(event -> event.getType() == RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD),
            any(ByteArrayOutputStream.class)
        );
        
        // Verify that the message was forwarded to the tunnel (since it was allowed)
        verify(writer).write(keyInstruction.toCharArray());
    }

    @Test
    void testHandleTextMessage_KeyboardInput_Blocked() throws Exception {
        // Arrange - Setup connection first
        URI uri = new URI("ws://localhost/guacamole/tunnel?token=test-jwt-token");
        when(webSocketSession.getUri()).thenReturn(uri);
        when(guacamoleRdpService.createRdpTunnel(anyString())).thenReturn(tunnel);
        
        // Establish connection
        handler.afterConnectionEstablished(webSocketSession);
        
        // Setup for keyboard input processing - block dangerous input
        when(rdpCommandProcessor.processInputEvent(any(ConnectedSystem.class), 
            any(RdpCommandProcessor.RdpInputEvent.class), 
            any(ByteArrayOutputStream.class))).thenReturn(false); // Block input
        
        // Guacamole keyboard instruction
        String keyInstruction = "3.key,5.65307,1.1;";
        TextMessage message = new TextMessage(keyInstruction);
        
        // Act
        handler.handleTextMessage(webSocketSession, message);
        
        // Assert
        // Verify that RdpCommandProcessor was called to analyze the input
        verify(rdpCommandProcessor).processInputEvent(
            any(ConnectedSystem.class),
            argThat(event -> event.getType() == RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD),
            any(ByteArrayOutputStream.class)
        );
        
        // Verify that the message was NOT forwarded to the tunnel (since it was blocked)
        verify(writer, never()).write(any(char[].class));
        
        // Verify that an error message was sent to the client
        verify(webSocketSession).sendMessage(argThat(msg -> {
            String payload = msg.getPayload().toString();
            return payload.contains("error") && payload.contains("blocked by security policy");
        }));
    }

    @Test
    void testHandleTextMessage_MouseClick_Analyzed() throws Exception {
        // Arrange - Setup connection first
        URI uri = new URI("ws://localhost/guacamole/tunnel?token=test-jwt-token");
        when(webSocketSession.getUri()).thenReturn(uri);
        when(guacamoleRdpService.createRdpTunnel(anyString())).thenReturn(tunnel);
        
        // Establish connection
        handler.afterConnectionEstablished(webSocketSession);
        
        // Setup for mouse click processing
        when(rdpCommandProcessor.processInputEvent(any(ConnectedSystem.class), 
            any(RdpCommandProcessor.RdpInputEvent.class), 
            any(ByteArrayOutputStream.class))).thenReturn(true); // Allow input
        
        // Guacamole mouse instruction: 5.mouse,3.100,3.200,1.1;
        // This represents a mouse click at coordinates (100, 200) with button mask 1 (left click)
        String mouseInstruction = "5.mouse,3.100,3.200,1.1;";
        TextMessage message = new TextMessage(mouseInstruction);
        
        // Act
        handler.handleTextMessage(webSocketSession, message);
        
        // Assert
        // Verify that RdpCommandProcessor was called to analyze the mouse click
        verify(rdpCommandProcessor).processInputEvent(
            any(ConnectedSystem.class),
            argThat(event -> event.getType() == RdpCommandProcessor.RdpInputEvent.RdpInputType.MOUSE_CLICK),
            any(ByteArrayOutputStream.class)
        );
        
        // Verify that the message was forwarded to the tunnel
        verify(writer).write(mouseInstruction.toCharArray());
    }

    @Test
    void testHandleTextMessage_MouseMove_Analyzed() throws Exception {
        // Arrange - Setup connection first
        URI uri = new URI("ws://localhost/guacamole/tunnel?token=test-jwt-token");
        when(webSocketSession.getUri()).thenReturn(uri);
        when(guacamoleRdpService.createRdpTunnel(anyString())).thenReturn(tunnel);
        
        // Establish connection
        handler.afterConnectionEstablished(webSocketSession);
        
        // Setup for mouse move processing
        when(rdpCommandProcessor.processInputEvent(any(ConnectedSystem.class), 
            any(RdpCommandProcessor.RdpInputEvent.class), 
            any(ByteArrayOutputStream.class))).thenReturn(true); // Allow input
        
        // Guacamole mouse instruction: 5.mouse,3.150,3.250,1.0;
        // This represents a mouse move at coordinates (150, 250) with button mask 0 (no button)
        String mouseInstruction = "5.mouse,3.150,3.250,1.0;";
        TextMessage message = new TextMessage(mouseInstruction);
        
        // Act
        handler.handleTextMessage(webSocketSession, message);
        
        // Assert
        // Verify that RdpCommandProcessor was called to analyze the mouse move
        verify(rdpCommandProcessor).processInputEvent(
            any(ConnectedSystem.class),
            argThat(event -> event.getType() == RdpCommandProcessor.RdpInputEvent.RdpInputType.MOUSE_MOVE),
            any(ByteArrayOutputStream.class)
        );
        
        // Verify that the message was forwarded to the tunnel
        verify(writer).write(mouseInstruction.toCharArray());
    }

    @Test
    void testHandleTextMessage_NonInputInstruction_NotAnalyzed() throws Exception {
        // Arrange - Setup connection first
        URI uri = new URI("ws://localhost/guacamole/tunnel?token=test-jwt-token");
        when(webSocketSession.getUri()).thenReturn(uri);
        when(guacamoleRdpService.createRdpTunnel(anyString())).thenReturn(tunnel);
        
        // Establish connection
        handler.afterConnectionEstablished(webSocketSession);
        
        // Guacamole size instruction (not an input event): 4.size,4.1024,3.768;
        String sizeInstruction = "4.size,4.1024,3.768;";
        TextMessage message = new TextMessage(sizeInstruction);
        
        // Act
        handler.handleTextMessage(webSocketSession, message);
        
        // Assert
        // Verify that RdpCommandProcessor was NOT called (this is not an input event)
        verify(rdpCommandProcessor, never()).processInputEvent(
            any(ConnectedSystem.class),
            any(RdpCommandProcessor.RdpInputEvent.class),
            any(ByteArrayOutputStream.class)
        );
        
        // Verify that the message was forwarded to the tunnel directly
        verify(writer).write(sizeInstruction.toCharArray());
    }

    @Test
    void testHandleTextMessage_ClipboardAccess_Analyzed() throws Exception {
        // Arrange - Setup connection first
        URI uri = new URI("ws://localhost/guacamole/tunnel?token=test-jwt-token");
        when(webSocketSession.getUri()).thenReturn(uri);
        when(guacamoleRdpService.createRdpTunnel(anyString())).thenReturn(tunnel);
        
        // Establish connection
        handler.afterConnectionEstablished(webSocketSession);
        
        // Setup for clipboard processing
        when(rdpCommandProcessor.processInputEvent(any(ConnectedSystem.class), 
            any(RdpCommandProcessor.RdpInputEvent.class), 
            any(ByteArrayOutputStream.class))).thenReturn(true); // Allow input
        
        // Guacamole clipboard instruction: 9.clipboard,10.text/plain,11.Hello World;
        String clipboardInstruction = "9.clipboard,10.text/plain,11.Hello World;";
        TextMessage message = new TextMessage(clipboardInstruction);
        
        // Act
        handler.handleTextMessage(webSocketSession, message);
        
        // Assert
        // Verify that RdpCommandProcessor was called to analyze clipboard access
        verify(rdpCommandProcessor).processInputEvent(
            any(ConnectedSystem.class),
            argThat(event -> event.getType() == RdpCommandProcessor.RdpInputEvent.RdpInputType.KEYBOARD &&
                event.getData().contains("clipboard")),
            any(ByteArrayOutputStream.class)
        );
        
        // Verify that the message was forwarded to the tunnel
        verify(writer).write(clipboardInstruction.toCharArray());
    }
}
