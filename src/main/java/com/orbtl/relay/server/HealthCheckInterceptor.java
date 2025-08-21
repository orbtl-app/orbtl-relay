package com.orbtl.relay.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * First handler in pipeline - intercepts health checks before they reach application logic.
 * NLB health checks connect and wait for TCP handshake, then disconnect without sending data.
 */
@Slf4j
public class HealthCheckInterceptor extends ChannelInboundHandlerAdapter {
    
    private static final int HEALTH_CHECK_TIMEOUT_MS = 50; // NLB doesn't send data
    private ScheduledFuture<?> healthCheckFuture;
    private boolean dataReceived = false;
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Schedule a check - if no data received in 50ms, it's a health check
        healthCheckFuture = ctx.executor().schedule(() -> {
            if (!dataReceived && ctx.channel().isActive()) {
                // This is a health check - just let it be, NLB will close it
                handleHealthCheck(ctx);
            }
        }, HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        
        // Don't propagate channelActive yet - wait to see if it's a health check
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Real client sent data - not a health check
        dataReceived = true;
        
        // Cancel the health check timer
        if (healthCheckFuture != null) {
            healthCheckFuture.cancel(false);
        }
        
        // Remove ourselves from pipeline - we're done
        ctx.pipeline().remove(this);
        
        // Fire the channelActive event now that we know it's a real client
        ctx.fireChannelActive();
        
        // Pass the message to next handler
        ctx.fireChannelRead(msg);
    }
    
    private void handleHealthCheck(ChannelHandlerContext ctx) {
        InetSocketAddress remoteAddr = (InetSocketAddress) ctx.channel().remoteAddress();
        log.trace("Health check from: {}", remoteAddr);
        
        // Health check successful - connection established
        // NLB will close it after confirming TCP handshake
        // We don't need to do anything else
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Cancel timer if still running
        if (healthCheckFuture != null) {
            healthCheckFuture.cancel(false);
        }
        
        if (!dataReceived) {
            // Was a health check that disconnected - don't propagate to other handlers
            log.trace("Health check disconnected");
        } else {
            // Real client disconnected - propagate to other handlers
            super.channelInactive(ctx);
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // Cancel timer if still running
        if (healthCheckFuture != null) {
            healthCheckFuture.cancel(false);
        }
        
        if (!dataReceived) {
            // Health check connection issue - just close quietly
            ctx.close();
        } else {
            // Real client issue - propagate
            super.exceptionCaught(ctx, cause);
        }
    }
}