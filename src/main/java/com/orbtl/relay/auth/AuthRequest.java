package com.orbtl.relay.auth;

import lombok.Data;

@Data
public class AuthRequest {
    private String apiKey;
    private String mode;  // "VIA_COPIER" or "DIRECT"
    private String challengeId;  // Used in VIA_COPIER mode
    private String mtAccount;  // Used in DIRECT mode
    private String brokerName;  // Used in DIRECT mode
}