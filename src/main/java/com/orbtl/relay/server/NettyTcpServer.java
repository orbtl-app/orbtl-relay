package com.orbtl.relay.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NettyTcpServer {
    
    private final RelayChannelHandler relayChannelHandler;
    
    @Value("${relay.tcp.port}")
    private int tcpPort;
    
    @Value("${relay.tcp.heartbeat-interval}")
    private String heartbeatInterval;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // Frame decoding - expects 4-byte length field at start
                            pipeline.addLast("frameDecoder", 
                                new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                            
                            // Frame encoding - prepends 4-byte length field
                            pipeline.addLast("frameEncoder", 
                                new LengthFieldPrepender(4));
                            
                            // Idle detection for heartbeat
                            pipeline.addLast("idleStateHandler", 
                                new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));
                            
                            // Business logic handler
                            pipeline.addLast("relayHandler", relayChannelHandler);
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);
            
            serverChannel = bootstrap.bind(tcpPort).sync().channel();
            log.info("Orbtl Relay TCP server started on port {}", tcpPort);
            
        } catch (Exception e) {
            log.error("Failed to start TCP server", e);
            shutdown();
        }
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Orbtl Relay TCP server");
        
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
}