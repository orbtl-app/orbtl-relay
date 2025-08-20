package com.orbtl.relay.auth;

import com.orbtl.relay.session.SessionContext;
import io.netty.util.AttributeKey;

public class AuthAttributes {
    public static final AttributeKey<Boolean> AUTHENTICATED = 
        AttributeKey.valueOf("authenticated");
    
    public static final AttributeKey<SessionContext> SESSION_CONTEXT = 
        AttributeKey.valueOf("sessionContext");
}