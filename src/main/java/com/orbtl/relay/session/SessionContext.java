package com.orbtl.relay.session;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class SessionContext {
    private String channelId;
    private String userId;
    private String challengeId;
    private String hedgeAccountId;
    private String mtAccount;
    private List<String> subscribedChannels;
    private boolean authenticated;
    private Instant connectedAt;
    private Instant authenticatedAt;
    private long messagesSent;
    private long messagesReceived;
}