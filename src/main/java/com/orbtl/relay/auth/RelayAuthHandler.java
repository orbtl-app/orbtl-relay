package com.orbtl.relay.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbtl.relay.session.SessionContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RelayAuthHandler {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${relay.auth.monolith-url}")
    private String monolithUrl;

    @Value("${relay.tcp.auth-timeout}")
    private String authTimeout;

    public void scheduleAuthTimeout(Channel channel) {
        channel.eventLoop().schedule(() -> {
            if (channel.isActive() && channel.attr(AuthAttributes.AUTHENTICATED).get() == null) {
                log.warn("Channel {} failed to authenticate within timeout", channel.id().asLongText());
                sendAuthFailed(channel, "Authentication timeout");
                channel.close();
            }
        }, 5, TimeUnit.SECONDS);
    }

    public Mono<SessionContext> authenticate(Channel channel, ByteBuf buffer) {
        try {
            // Parse auth request from buffer
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            String json = new String(bytes, StandardCharsets.UTF_8);

            AuthRequest authRequest = objectMapper.readValue(json, AuthRequest.class);
            log.debug("Auth request received - Mode: {}, Challenge: {}, MT: {}",
                authRequest.getMode(), authRequest.getChallengeId(), authRequest.getMtAccount());

            // Call monolith to validate
            WebClient webClient = webClientBuilder
                .baseUrl(monolithUrl)
                .build();
            
            log.info("Sending auth request to {}/api/auth/validate-ea-key with API key: {}",
                monolithUrl, authRequest.getApiKey().substring(0, Math.min(10, authRequest.getApiKey().length())) + "...");

            return webClient.post()
                .uri("/api/auth/validate-ea-key")
                .header("X-API-Key", authRequest.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(authRequest)
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .map(response -> {
                    if (!response.isSuccess()) {
                        throw new RuntimeException("Authentication failed: " + response.getMessage());
                    }

                    // Create session context
                    SessionContext context = SessionContext.builder()
                        .channelId(channel.id().asLongText())
                        .userId(response.getUserId())
                        .challengeId(response.getChallengeId())
                        .hedgeAccountId(response.getHedgeAccountId())
                        .subscribedChannels(List.of(
                            "challenge:" + response.getChallengeId() + ":ack",
                            "hedge:account:" + response.getHedgeAccountId()
                        ))
                        .authenticated(true)
                        .connectedAt(Instant.now())
                        .authenticatedAt(Instant.now())
                        .messagesSent(0L)
                        .messagesReceived(0L)
                        .build();

                    // Mark channel as authenticated
                    channel.attr(AuthAttributes.AUTHENTICATED).set(true);
                    channel.attr(AuthAttributes.SESSION_CONTEXT).set(context);

                    // Send success response
                    sendAuthSuccess(channel, response);

                    log.info("Authentication successful - User: {}, Challenge: {}",
                        response.getUserId(), response.getChallengeId());

                    return context;
                })
                .onErrorResume(error -> {
                    log.error("Authentication failed: {}", error.getMessage());
                    sendAuthFailed(channel, error.getMessage());
                    channel.close();
                    return Mono.empty();
                });

        } catch (Exception e) {
            log.error("Failed to parse auth request", e);
            sendAuthFailed(channel, "Invalid auth request format");
            channel.close();
            return Mono.empty();
        }
    }

    private void sendAuthSuccess(Channel channel, AuthResponse response) {
        String message = String.format("AUTH_SUCCESS|%s|%s|%s",
            response.getUserId(),
            response.getChallengeId(),
            response.getHedgeAccountId());

        ByteBuf buffer = Unpooled.copiedBuffer(message, StandardCharsets.UTF_8);
        channel.writeAndFlush(buffer);
    }

    private void sendAuthFailed(Channel channel, String reason) {
        String message = "AUTH_FAILED|" + reason;
        ByteBuf buffer = Unpooled.copiedBuffer(message, StandardCharsets.UTF_8);
        channel.writeAndFlush(buffer);
    }
}