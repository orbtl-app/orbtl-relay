package com.orbtl.relay.routing;

import com.orbtl.relay.protocol.FrameType;
import com.orbtl.relay.session.SessionContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRouter implements MessageListener {
    
    private final ReactiveRedisTemplate<String, byte[]> redisTemplate;
    private final ReactiveRedisMessageListenerContainer messageListenerContainer;
    
    // Map of Redis channel to Netty channels
    private final Map<String, Map<String, Channel>> subscriptions = new ConcurrentHashMap<>();
    
    public void subscribeToChannels(Channel channel, SessionContext context) {
        String channelId = channel.id().asLongText();
        
        // Subscribe to each Redis channel
        for (String redisChannel : context.getSubscribedChannels()) {
            subscribeToRedisChannel(redisChannel, channelId, channel);
            log.debug("Subscribed channel {} to Redis channel: {}", channelId, redisChannel);
        }
    }
    
    public void unsubscribeFromChannels(SessionContext context) {
        String channelId = context.getChannelId();
        
        // Unsubscribe from all Redis channels
        for (String redisChannel : context.getSubscribedChannels()) {
            unsubscribeFromRedisChannel(redisChannel, channelId);
            log.debug("Unsubscribed channel {} from Redis channel: {}", channelId, redisChannel);
        }
    }
    
    public void routeMessage(SessionContext session, FrameType frameType, ByteBuf buffer) {
        // Skip frame type byte
        buffer.skipBytes(1);
        
        // Extract message payload
        byte[] payload = new byte[buffer.readableBytes()];
        buffer.readBytes(payload);
        
        // Determine target channel based on frame type
        String targetChannel = determineTargetChannel(session, frameType);
        
        if (targetChannel != null) {
            // Publish to Redis
            redisTemplate.convertAndSend(targetChannel, payload)
                .subscribe(
                    result -> {
                        session.setMessagesSent(session.getMessagesSent() + 1);
                        log.trace("Routed message to Redis channel: {}", targetChannel);
                    },
                    error -> log.error("Failed to publish to Redis", error)
                );
        }
    }
    
    private String determineTargetChannel(SessionContext session, FrameType frameType) {
        return switch (frameType) {
            case ROUTE_HEDGE -> "hedge:account:" + session.getHedgeAccountId();
            case ROUTE_CHALLENGE -> "challenge:" + session.getChallengeId();
            case BROADCAST -> "ea:broadcast:" + session.getUserId();
            default -> {
                log.warn("Unknown routing type: {}", frameType);
                yield null;
            }
        };
    }
    
    private void subscribeToRedisChannel(String redisChannel, String channelId, Channel nettyChannel) {
        // Add to local map
        subscriptions.computeIfAbsent(redisChannel, k -> new ConcurrentHashMap<>())
            .put(channelId, nettyChannel);
        
        // Subscribe to Redis if first subscriber
        if (subscriptions.get(redisChannel).size() == 1) {
            messageListenerContainer.receive(ChannelTopic.of(redisChannel))
                .subscribe(message -> {
                    // Route message to all subscribed Netty channels
                    Map<String, Channel> channels = subscriptions.get(redisChannel);
                    if (channels != null) {
                        // ReactiveSubscription.Message contains channel (String) and message (String by default)
                        // We need to handle String messages and convert them to bytes
                        String messageStr = message.getMessage();
                        byte[] payload = messageStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        
                        channels.values().forEach(ch -> {
                            if (ch.isActive()) {
                                ByteBuf buffer = Unpooled.copiedBuffer(payload);
                                ch.writeAndFlush(buffer);
                            }
                        });
                    }
                });
        }
    }
    
    private void unsubscribeFromRedisChannel(String redisChannel, String channelId) {
        Map<String, Channel> channels = subscriptions.get(redisChannel);
        if (channels != null) {
            channels.remove(channelId);
            
            // If no more subscribers, remove the Redis subscription
            if (channels.isEmpty()) {
                subscriptions.remove(redisChannel);
                // Note: ReactiveRedisMessageListenerContainer handles cleanup automatically
            }
        }
    }
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        // This is called when a message is received from Redis
        String channel = new String(message.getChannel());
        byte[] payload = message.getBody();
        
        Map<String, Channel> nettyChannels = subscriptions.get(channel);
        if (nettyChannels != null) {
            nettyChannels.values().forEach(ch -> {
                if (ch.isActive()) {
                    ByteBuf buffer = Unpooled.copiedBuffer(payload);
                    ch.writeAndFlush(buffer);
                }
            });
        }
    }
}