//+------------------------------------------------------------------+
//|                                                       TestEA.mq5 |
//|                                      Copyright 2024, Orbtl Inc. |
//|                                             https://orbtl.app   |
//+------------------------------------------------------------------+
#property copyright "Copyright 2024, Orbtl Inc."
#property link      "https://orbtl.app"
#property version   "1.00"

#include "OrbtlRelay.mqh"

// Input parameters
input string ApiKey = "";  // Your Orbtl API Key
input CONNECTION_MODE ConnectionMode = MODE_DIRECT;  // Connection mode
input int HeartbeatIntervalSeconds = 30;  // Heartbeat interval

// Global variables
OrbtlRelay relay;
datetime lastHeartbeat;
datetime lastTradeSent;

//+------------------------------------------------------------------+
//| Expert initialization function                                   |
//+------------------------------------------------------------------+
int OnInit() {
    if (StringLen(ApiKey) == 0) {
        Alert("API Key is required!");
        return INIT_FAILED;
    }
    
    Print("Initializing Orbtl Relay...");
    
    if (!relay.Initialize(ApiKey, ConnectionMode)) {
        Alert("Failed to initialize Orbtl Relay!");
        return INIT_FAILED;
    }
    
    Print("Successfully connected to Orbtl");
    Print("Challenge ID: ", relay.GetChallengeId());
    Print("Hedge Account ID: ", relay.GetHedgeAccountId());
    
    lastHeartbeat = TimeCurrent();
    lastTradeSent = 0;
    
    return INIT_SUCCEEDED;
}

//+------------------------------------------------------------------+
//| Expert deinitialization function                                 |
//+------------------------------------------------------------------+
void OnDeinit(const int reason) {
    Print("Disconnecting from Orbtl Bridge...");
    relay.Disconnect();
}

//+------------------------------------------------------------------+
//| Expert tick function                                             |
//+------------------------------------------------------------------+
void OnTick() {
    // Check connection
    if (!relay.IsConnected()) {
        Print("Connection lost, attempting to reconnect...");
        if (!relay.Initialize(ApiKey, ConnectionMode)) {
            return;
        }
    }
    
    // Send heartbeat
    if (TimeCurrent() - lastHeartbeat >= HeartbeatIntervalSeconds) {
        relay.SendHeartbeat();
        lastHeartbeat = TimeCurrent();
    }
    
    // Check for new trades to send to hedge account
    CheckAndSendNewTrades();
}

//+------------------------------------------------------------------+
//| Check for new trades and send hedge requests                    |
//+------------------------------------------------------------------+
void CheckAndSendNewTrades() {
    int total = PositionsTotal();
    
    for (int i = 0; i < total; i++) {
        if (PositionSelectByTicket(PositionGetTicket(i))) {
            datetime openTime = (datetime)PositionGetInteger(POSITION_TIME);
            
            // Only send trades opened after last check
            if (openTime > lastTradeSent) {
                SendHedgeRequest();
                lastTradeSent = openTime;
            }
        }
    }
}

//+------------------------------------------------------------------+
//| Send hedge request for current position                         |
//+------------------------------------------------------------------+
void SendHedgeRequest() {
    string symbol = PositionGetString(POSITION_SYMBOL);
    double volume = PositionGetDouble(POSITION_VOLUME);
    long positionType = PositionGetInteger(POSITION_TYPE);
    double openPrice = PositionGetDouble(POSITION_PRICE_OPEN);
    long ticket = PositionGetInteger(POSITION_TICKET);
    
    // Build hedge request message
    string hedgeMessage = StringFormat(
        "{\"type\":\"OPEN_POSITION\",\"challengeId\":\"%s\",\"ticket\":%d,\"symbol\":\"%s\",\"lots\":%.2f,\"direction\":\"%s\",\"openPrice\":%.5f,\"timestamp\":%d}",
        relay.GetChallengeId(),
        ticket,
        symbol,
        volume,
        (positionType == POSITION_TYPE_BUY) ? "BUY" : "SELL",
        openPrice,
        TimeCurrent()
    );
    
    if (relay.SendHedgeMessage(hedgeMessage)) {
        Print("Hedge request sent for ticket ", ticket);
    } else {
        Print("Failed to send hedge request for ticket ", ticket);
    }
}

//+------------------------------------------------------------------+
//| Handle trade events                                             |
//+------------------------------------------------------------------+
void OnTrade() {
    // Handle trade close events
    if (HistorySelect(TimeCurrent() - 60, TimeCurrent())) {
        int deals = HistoryDealsTotal();
        
        for (int i = deals - 1; i >= 0; i--) {
            ulong dealTicket = HistoryDealGetTicket(i);
            
            if (HistoryDealGetInteger(dealTicket, DEAL_ENTRY) == DEAL_ENTRY_OUT) {
                SendCloseNotification(dealTicket);
            }
        }
    }
}

//+------------------------------------------------------------------+
//| Send close notification for hedge                               |
//+------------------------------------------------------------------+
void SendCloseNotification(ulong dealTicket) {
    long positionId = HistoryDealGetInteger(dealTicket, DEAL_POSITION_ID);
    double closePrice = HistoryDealGetDouble(dealTicket, DEAL_PRICE);
    double profit = HistoryDealGetDouble(dealTicket, DEAL_PROFIT);
    
    string closeMessage = StringFormat(
        "{\"type\":\"CLOSE_POSITION\",\"challengeId\":\"%s\",\"ticket\":%d,\"closePrice\":%.5f,\"profit\":%.2f,\"timestamp\":%d}",
        relay.GetChallengeId(),
        positionId,
        closePrice,
        profit,
        TimeCurrent()
    );
    
    if (relay.SendHedgeMessage(closeMessage)) {
        Print("Close notification sent for position ", positionId);
    }
}

//+------------------------------------------------------------------+