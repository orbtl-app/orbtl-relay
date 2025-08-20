package com.orbtl.relay.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private boolean success;
    private String userId;
    private String challengeId;
    private String hedgeAccountId;
    private String message;
}