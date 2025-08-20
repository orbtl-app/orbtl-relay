package com.orbtl.relay.protocol;

public enum FrameType {
    AUTH((byte) 0x01),           // Authentication frame
    ROUTE_HEDGE((byte) 0x02),    // Route to hedge account
    ROUTE_CHALLENGE((byte) 0x03), // Route to challenge
    BROADCAST((byte) 0x04),      // Broadcast to group
    HEARTBEAT((byte) 0x05),      // Keep-alive
    AUTH_SUCCESS((byte) 0x10),   // Auth success response
    AUTH_FAILED((byte) 0x11);    // Auth failed response
    
    private final byte value;
    
    FrameType(byte value) {
        this.value = value;
    }
    
    public byte getValue() {
        return value;
    }
    
    public static FrameType fromByte(byte value) {
        for (FrameType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown frame type: " + value);
    }
}