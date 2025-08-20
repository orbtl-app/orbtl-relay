package com.orbtl.relay.session;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SessionManager {
    
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    
    public void registerChannel(Channel channel) {
        String channelId = channel.id().asLongText();
        channels.put(channelId, channel);
        log.debug("Channel registered: {}", channelId);
    }
    
    public void authenticateSession(Channel channel, SessionContext context) {
        String channelId = channel.id().asLongText();
        context.setChannelId(channelId);
        context.setAuthenticatedAt(Instant.now());
        sessions.put(channelId, context);
        log.info("Session authenticated - Channel: {}, User: {}, Challenge: {}", 
            channelId, context.getUserId(), context.getChallengeId());
    }
    
    public SessionContext getSession(String channelId) {
        return sessions.get(channelId);
    }
    
    public Channel getChannel(String channelId) {
        return channels.get(channelId);
    }
    
    public boolean isAuthenticated(String channelId) {
        SessionContext session = sessions.get(channelId);
        return session != null && session.isAuthenticated();
    }
    
    public void removeSession(Channel channel) {
        String channelId = channel.id().asLongText();
        SessionContext session = sessions.remove(channelId);
        channels.remove(channelId);
        
        if (session != null) {
            log.info("Session removed - Channel: {}, User: {}, Challenge: {}", 
                channelId, session.getUserId(), session.getChallengeId());
        }
    }
    
    public Map<String, SessionContext> getAllSessions() {
        return new ConcurrentHashMap<>(sessions);
    }
    
    public int getActiveSessionCount() {
        return sessions.size();
    }
}