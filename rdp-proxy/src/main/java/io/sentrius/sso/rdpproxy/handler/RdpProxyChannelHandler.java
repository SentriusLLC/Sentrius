package io.sentrius.sso.rdpproxy.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.RdpListenerService;
import io.sentrius.sso.core.services.SessionService;
import io.sentrius.sso.core.services.TerminalService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.metadata.TerminalSessionMetadataService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.rdpproxy.config.RdpProxyConfig;
import io.sentrius.sso.rdpproxy.service.RdpConnectionManager;
import io.sentrius.sso.rdpproxy.streams.RdpSessionRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * RDP channel handler that integrates with Sentrius safeguards.
 * Handles incoming RDP connections and applies monitoring and rules.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RdpProxyChannelHandler extends ChannelInboundHandlerAdapter {

    private final RdpProxyConfig config;
    private final RdpConnectionManager connectionManager;
    private final HostGroupService hostGroupService;
    private final UserService userService;
    private final SessionService sessionService;
    private final TerminalService terminalService;
    private final RdpListenerService rdpListenerService;
    private final CryptoService cryptoService;
    private final TerminalSessionMetadataService terminalSessionMetadataService;
    
    @Qualifier("rdpTaskExecutor")
    private final Executor taskExecutor;

    private final ConcurrentMap<String, RdpSessionRoute> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("New RDP connection from: {}", ctx.channel().remoteAddress());
        
        // Create session route for this connection
        String sessionId = ctx.channel().id().asShortText();
        RdpSessionRoute sessionRoute = RdpSessionRoute.builder()
            .hostGroupService(hostGroupService)
            .terminalService(terminalService)
            .sessionService(sessionService)
            .cryptoService(cryptoService)
            .rdpListenerService(rdpListenerService)
            .terminalSessionMetadataService(terminalSessionMetadataService)
            .build();
            
        activeSessions.put(sessionId, sessionRoute);
        
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        String sessionId = ctx.channel().id().asShortText();
        
        try {
            log.debug("Received RDP data from session {}: {} bytes", sessionId, byteBuf.readableBytes());
            
            // Get session route
            RdpSessionRoute sessionRoute = activeSessions.get(sessionId);
            if (sessionRoute != null) {
                // Process RDP data through Sentrius monitoring
                connectionManager.processRdpData(sessionRoute, byteBuf, ctx);
            } else {
                log.warn("No session route found for session: {}", sessionId);
                ctx.close();
            }
            
        } catch (Exception e) {
            log.error("Error processing RDP data for session: " + sessionId, e);
            ctx.close();
        } finally {
            byteBuf.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String sessionId = ctx.channel().id().asShortText();
        log.info("RDP connection closed: {}", ctx.channel().remoteAddress());
        
        // Clean up session using connection manager
        connectionManager.cleanupSession(sessionId);
        
        // Clean up session route
        RdpSessionRoute sessionRoute = activeSessions.remove(sessionId);
        if (sessionRoute != null) {
            sessionRoute.cleanup(sessionId);
        }
        
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String sessionId = ctx.channel().id().asShortText();
        log.error("Exception in RDP connection: " + sessionId, cause);
        
        // Clean up session using connection manager
        connectionManager.cleanupSession(sessionId);
        
        // Clean up session route
        RdpSessionRoute sessionRoute = activeSessions.remove(sessionId);
        if (sessionRoute != null) {
            sessionRoute.cleanup(sessionId);
        }
        
        ctx.close();
    }
}