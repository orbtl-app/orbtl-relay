//+------------------------------------------------------------------+
//|                                                  OrbtlBridge.mqh |
//|                                      Copyright 2024, Orbtl Inc. |
//|                                             https://orbtl.app   |
//+------------------------------------------------------------------+
#property copyright "Copyright 2024, Orbtl Inc."
#property link      "https://orbtl.app"
#property version   "1.00"

enum CONNECTION_MODE {
    MODE_VIA_COPIER,     // Connect through local Trade Copier
    MODE_DIRECT          // Connect directly to EA Bridge
};

enum FRAME_TYPE {
    FRAME_AUTH = 0x01,
    FRAME_ROUTE_HEDGE = 0x02,
    FRAME_ROUTE_CHALLENGE = 0x03,
    FRAME_BROADCAST = 0x04,
    FRAME_HEARTBEAT = 0x05,
    FRAME_AUTH_SUCCESS = 0x10,
    FRAME_AUTH_FAILED = 0x11
};

class OrbtlBridge {
private:
    int tcpSocket;
    int copierSocket;
    CONNECTION_MODE mode;
    string apiKey;
    string challengeId;
    string hedgeAccountId;
    string userId;
    bool authenticated;
    bool copierConnected;
    
public:
    OrbtlBridge() {
        tcpSocket = -1;
        copierSocket = -1;
        authenticated = false;
        copierConnected = false;
    }
    
    ~OrbtlBridge() {
        Disconnect();
    }
    
    bool Initialize(string _apiKey, CONNECTION_MODE _mode = MODE_VIA_COPIER) {
        apiKey = _apiKey;
        mode = _mode;
        
        if (mode == MODE_VIA_COPIER) {
            return ConnectViaCopier();
        } else {
            return ConnectDirect();
        }
    }
    
    bool ConnectViaCopier() {
        // Step 1: Connect to local Trade Copier
        copierSocket = SocketCreate();
        if (copierSocket == INVALID_HANDLE) {
            Print("Failed to create copier socket");
            return false;
        }
        
        if (!SocketConnect(copierSocket, "127.0.0.1", 7001, 5000)) {
            Print("Failed to connect to Trade Copier on port 7001");
            SocketClose(copierSocket);
            copierSocket = -1;
            return false;
        }
        
        Print("Connected to Trade Copier");
        copierConnected = true;
        
        // Step 2: Wait for challenge assignment
        string response = ReceiveFromCopier();
        if (StringFind(response, "CHALLENGE_ASSIGNED") >= 0) {
            ParseChallengeAssignment(response);
        }
        
        // Step 3: Connect to EA Bridge
        return ConnectToBridge();
    }
    
    bool ConnectDirect() {
        // Connect directly to EA Bridge
        if (!ConnectToBridge()) {
            return false;
        }
        
        // Send auth with MT account info
        string authJson = BuildDirectAuthJson();
        return SendAuth(authJson);
    }
    
    bool ConnectToBridge() {
        tcpSocket = SocketCreate();
        if (tcpSocket == INVALID_HANDLE) {
            Print("Failed to create bridge socket");
            return false;
        }
        
        // Connect to EA Bridge (adjust host/port as needed)
        if (!SocketConnect(tcpSocket, "localhost", 9090, 5000)) {
            Print("Failed to connect to EA Bridge on port 9090");
            SocketClose(tcpSocket);
            tcpSocket = -1;
            return false;
        }
        
        Print("Connected to EA Bridge");
        
        // Send authentication
        string authJson;
        if (mode == MODE_VIA_COPIER) {
            authJson = BuildCopierAuthJson();
        } else {
            authJson = BuildDirectAuthJson();
        }
        
        return SendAuth(authJson);
    }
    
    bool SendAuth(string authJson) {
        // Build auth frame
        uchar data[];
        StringToCharArray(authJson, data);
        
        // Create frame with type byte
        uchar frame[];
        int frameSize = ArraySize(data) + 1;  // +1 for frame type
        ArrayResize(frame, frameSize);
        
        frame[0] = FRAME_AUTH;
        for (int i = 0; i < ArraySize(data); i++) {
            frame[i + 1] = data[i];
        }
        
        // Send with length prefix (4 bytes)
        if (!SendFrame(frame)) {
            Print("Failed to send auth frame");
            return false;
        }
        
        // Wait for auth response
        string response = ReceiveFromBridge();
        if (StringFind(response, "AUTH_SUCCESS") >= 0) {
            ParseAuthResponse(response);
            authenticated = true;
            Print("Authentication successful - Challenge: ", challengeId);
            return true;
        } else {
            Print("Authentication failed: ", response);
            return false;
        }
    }
    
    bool SendFrame(uchar &frame[]) {
        int frameSize = ArraySize(frame);
        
        // Send 4-byte length prefix
        uchar lengthBytes[4];
        lengthBytes[0] = (uchar)((frameSize >> 24) & 0xFF);
        lengthBytes[1] = (uchar)((frameSize >> 16) & 0xFF);
        lengthBytes[2] = (uchar)((frameSize >> 8) & 0xFF);
        lengthBytes[3] = (uchar)(frameSize & 0xFF);
        
        if (SocketSend(tcpSocket, lengthBytes, 4) != 4) {
            return false;
        }
        
        // Send frame
        return SocketSend(tcpSocket, frame, frameSize) == frameSize;
    }
    
    bool SendHedgeMessage(string message) {
        if (!authenticated) {
            Print("Not authenticated");
            return false;
        }
        
        uchar data[];
        StringToCharArray(message, data);
        
        uchar frame[];
        ArrayResize(frame, ArraySize(data) + 1);
        frame[0] = FRAME_ROUTE_HEDGE;
        
        for (int i = 0; i < ArraySize(data); i++) {
            frame[i + 1] = data[i];
        }
        
        return SendFrame(frame);
    }
    
    bool SendChallengeMessage(string message) {
        if (!authenticated) {
            Print("Not authenticated");
            return false;
        }
        
        uchar data[];
        StringToCharArray(message, data);
        
        uchar frame[];
        ArrayResize(frame, ArraySize(data) + 1);
        frame[0] = FRAME_ROUTE_CHALLENGE;
        
        for (int i = 0; i < ArraySize(data); i++) {
            frame[i + 1] = data[i];
        }
        
        return SendFrame(frame);
    }
    
    bool SendHeartbeat() {
        uchar frame[1];
        frame[0] = FRAME_HEARTBEAT;
        return SendFrame(frame);
    }
    
    string ReceiveFromBridge() {
        uchar buffer[1024];
        int received = SocketRead(tcpSocket, buffer, 1024, 5000);
        
        if (received > 0) {
            return CharArrayToString(buffer, 0, received);
        }
        
        return "";
    }
    
    string ReceiveFromCopier() {
        uchar buffer[1024];
        int received = SocketRead(copierSocket, buffer, 1024, 5000);
        
        if (received > 0) {
            return CharArrayToString(buffer, 0, received);
        }
        
        return "";
    }
    
    void Disconnect() {
        if (tcpSocket != -1) {
            SocketClose(tcpSocket);
            tcpSocket = -1;
        }
        
        if (copierSocket != -1) {
            SocketClose(copierSocket);
            copierSocket = -1;
        }
        
        authenticated = false;
        copierConnected = false;
    }
    
    bool IsConnected() {
        return tcpSocket != -1 && authenticated;
    }
    
    string GetChallengeId() {
        return challengeId;
    }
    
    string GetHedgeAccountId() {
        return hedgeAccountId;
    }
    
private:
    string BuildCopierAuthJson() {
        return StringFormat(
            "{\"apiKey\":\"%s\",\"mode\":\"VIA_COPIER\",\"challengeId\":\"%s\"}",
            apiKey, challengeId
        );
    }
    
    string BuildDirectAuthJson() {
        return StringFormat(
            "{\"apiKey\":\"%s\",\"mode\":\"DIRECT\",\"mtAccount\":\"%d\",\"brokerName\":\"%s\"}",
            apiKey, AccountInfoInteger(ACCOUNT_LOGIN), AccountInfoString(ACCOUNT_COMPANY)
        );
    }
    
    void ParseChallengeAssignment(string response) {
        // Format: CHALLENGE_ASSIGNED|challengeId|hedgeAccountId
        string parts[];
        StringSplit(response, '|', parts);
        
        if (ArraySize(parts) >= 3) {
            challengeId = parts[1];
            hedgeAccountId = parts[2];
        }
    }
    
    void ParseAuthResponse(string response) {
        // Format: AUTH_SUCCESS|userId|challengeId|hedgeAccountId
        string parts[];
        StringSplit(response, '|', parts);
        
        if (ArraySize(parts) >= 4) {
            userId = parts[1];
            challengeId = parts[2];
            hedgeAccountId = parts[3];
        }
    }
};