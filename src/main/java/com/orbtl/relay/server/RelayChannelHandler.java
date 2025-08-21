package com.orbtl.relay.server;

import com.orbtl.relay.auth.AuthAttributes;
import com.orbtl.relay.auth.RelayAuthHandler;
import com.orbtl.relay.protocol.FrameType;
import com.orbtl.relay.routing.MessageRouter;
import com.orbtl.relay.session.SessionContext;
import com.orbtl.relay.session.SessionManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class RelayChannelHandler extends ChannelInboundHandlerAdapter {
    
    private final SessionManager sessionManager;
    private final RelayAuthHandler authHandler;
    private final MessageRouter messageRouter;
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // Only log at debug level initially - will log at info level after successful auth
        log.debug("New connection from: {}", ctx.channel().remoteAddress());
        sessionManager.registerChannel(ctx.channel());
        authHandler.scheduleAuthTimeout(ctx.channel());
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buffer = (ByteBuf) msg;
        
        try {
            // Check if authenticated
            Boolean authenticated = ctx.channel().attr(AuthAttributes.AUTHENTICATED).get();
            
            if (authenticated == null || !authenticated) {
                // First message must be auth
                handleAuthentication(ctx, buffer);
            } else {
                // Route message
                handleMessage(ctx, buffer);
            }
        } finally {
            buffer.release();
        }
    }
    
    private void handleAuthentication(ChannelHandlerContext ctx, ByteBuf buffer) {
        // Check frame type
        if (buffer.readableBytes() < 1) {
            log.warn("Empty auth message received");
            ctx.close();
            return;
        }
        
        byte frameType = buffer.getByte(0);
        if (frameType != FrameType.AUTH.getValue()) {
            log.warn("First message must be AUTH, got: {}", frameType);
            ctx.close();
            return;
        }
        
        // Skip frame type byte
        buffer.skipBytes(1);
        
        // Authenticate
        authHandler.authenticate(ctx.channel(), buffer)
            .subscribe(
                context -> {
                    log.info("EA authenticated successfully: {} from {}", 
                        context.getMtAccount(), ctx.channel().remoteAddress());
                    sessionManager.authenticateSession(ctx.channel(), context);
                    messageRouter.subscribeToChannels(ctx.channel(), context);
                },
                error -> {
                    log.error("Authentication failed", error);
                    ctx.close();
                }
            );
    }
    
    private void handleMessage(ChannelHandlerContext ctx, ByteBuf buffer) {
        SessionContext session = ctx.channel().attr(AuthAttributes.SESSION_CONTEXT).get();
        
        if (session == null) {
            log.error("No session context found for authenticated channel");
            ctx.close();
            return;
        }
        
        // Update stats
        session.setMessagesReceived(session.getMessagesReceived() + 1);
        
        // Check frame type
        if (buffer.readableBytes() < 1) {
            return;
        }
        
        byte frameTypeByte = buffer.getByte(0);
        FrameType frameType;
        
        try {
            frameType = FrameType.fromByte(frameTypeByte);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown frame type: {}", frameTypeByte);
            return;
        }
        
        // Handle based on type
        switch (frameType) {
            case HEARTBEAT:
                // Just acknowledge we're alive
                log.trace("Heartbeat from channel: {}", ctx.channel().id());
                break;
                
            case ROUTE_HEDGE:
            case ROUTE_CHALLENGE:
            case BROADCAST:
                // Route the message
                messageRouter.routeMessage(session, frameType, buffer);
                break;
                
            default:
                log.warn("Unexpected frame type after auth: {}", frameType);
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("Channel {} idle for too long, closing", ctx.channel().id());
                ctx.close();
            }
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        SessionContext session = ctx.channel().attr(AuthAttributes.SESSION_CONTEXT).get();
        
        // Only log disconnection for authenticated sessions (not health checks)
        if (session != null) {
            log.info("EA disconnected: {} ({})", session.getMtAccount(), ctx.channel().remoteAddress());
            messageRouter.unsubscribeFromChannels(session);
        } else {
            // This was likely a health check or unauthenticated connection
            log.debug("Connection closed: {}", ctx.channel().remoteAddress());
        }
        
        sessionManager.removeSession(ctx.channel());
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Check if it's just a connection reset (common with health checks)
        if (cause.getMessage() != null && cause.getMessage().contains("Connection reset")) {
            log.debug("Connection reset: {}", ctx.channel().remoteAddress());
        } else {
            log.error("Channel exception", cause);
        }
        ctx.close();
    }
}