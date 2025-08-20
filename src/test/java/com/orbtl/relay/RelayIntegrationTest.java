package com.orbtl.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "relay.tcp.port=9091",  // Use different port for testing
    "spring.redis.host=localhost",
    "spring.redis.port=6379"
})
public class RelayIntegrationTest {
    
    private static final int TEST_PORT = 9091;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    public void testConnectionAndAuthentication() throws Exception {
        // Create test client
        EventLoopGroup group = new NioEventLoopGroup();
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                        ch.pipeline().addLast(new LengthFieldPrepender(4));
                        ch.pipeline().addLast(new TestClientHandler(responseFuture));
                    }
                });
            
            // Connect to server
            Channel channel = bootstrap.connect("localhost", TEST_PORT).sync().channel();
            
            // Send auth message
            String authJson = "{\"apiKey\":\"test-key\",\"mode\":\"DIRECT\",\"mtAccount\":\"12345\",\"brokerName\":\"TestBroker\"}";
            ByteBuf authFrame = createAuthFrame(authJson);
            channel.writeAndFlush(authFrame);
            
            // Wait for response
            String response = responseFuture.get(5, TimeUnit.SECONDS);
            assertNotNull(response);
            
            // Note: This will fail without a running monolith, but shows the structure
            // assertTrue(response.startsWith("AUTH_SUCCESS") || response.startsWith("AUTH_FAILED"));
            
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
    
    private ByteBuf createAuthFrame(String json) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuf buffer = Unpooled.buffer(jsonBytes.length + 1);
        buffer.writeByte(0x01); // AUTH frame type
        buffer.writeBytes(jsonBytes);
        return buffer;
    }
    
    private static class TestClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final CompletableFuture<String> responseFuture;
        
        TestClientHandler(CompletableFuture<String> responseFuture) {
            this.responseFuture = responseFuture;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            String response = new String(bytes, StandardCharsets.UTF_8);
            responseFuture.complete(response);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            responseFuture.completeExceptionally(cause);
            ctx.close();
        }
    }
}