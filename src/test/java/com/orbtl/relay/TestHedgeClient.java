package com.orbtl.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test client that simulates the hedge system receiving and processing trades
 */
public class TestHedgeClient {
    
    private static final String API_KEY = "orbk_wzy-zKMeo3BpduPdIyWQcjWbofsOSS1n2WabsU1QH50";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<Integer, HedgePosition> positions = new HashMap<>();
    
    static class HedgePosition {
        String symbol;
        String operation;
        double volume;
        double openPrice;
        long timestamp;
        
        HedgePosition(String symbol, String operation, double volume, double openPrice, long timestamp) {
            this.symbol = symbol;
            this.operation = operation;
            this.volume = volume;
            this.openPrice = openPrice;
            this.timestamp = timestamp;
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Hedge System Client ===");
        System.out.println("Connecting to Orbtl Relay...");
        
        try (Socket socket = new Socket("localhost", 9090)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            
            // Authenticate as hedge system
            String authJson = String.format(
                "{\"apiKey\":\"%s\",\"mode\":\"DIRECT\",\"mtAccount\":\"HEDGE_MASTER\",\"brokerName\":\"HedgeBroker\"}",
                API_KEY
            );
            
            sendFrame(out, (byte) 0x01, authJson);
            System.out.println("Sent auth request as HEDGE_MASTER...");
            
            // Read auth response
            String authResponse = readFrame(in);
            System.out.println("Auth Response: " + authResponse);
            
            if (!authResponse.contains("AUTH_SUCCESS")) {
                System.err.println("Authentication failed!");
                return;
            }
            
            System.out.println("Authentication successful!");
            System.out.println("----------------------------------------");
            System.out.println("Hedge System Active - Waiting for trades to hedge...");
            System.out.println("Press Ctrl+C to stop");
            System.out.println("----------------------------------------\n");
            
            // Process incoming trades
            while (running.get()) {
                try {
                    String message = readFrame(in);
                    if (message != null && !message.isEmpty()) {
                        // Skip auth responses
                        if (message.startsWith("AUTH_")) {
                            continue;
                        }
                        
                        // Parse the JSON message
                        JsonNode json = objectMapper.readTree(message);
                        String type = json.get("type").asText();
                        
                        System.out.println("\n[RECEIVED] Trade signal from EA:");
                        System.out.println("  Raw: " + message);
                        
                        if ("TRADE".equals(type)) {
                            handleTradeMessage(json, out);
                        } else if ("STATUS".equals(type)) {
                            handleStatusMessage(json);
                        } else {
                            System.out.println("  Type: " + type + " (no hedge action needed)");
                        }
                        
                        System.out.println("----------------------------------------");
                    }
                } catch (EOFException e) {
                    System.out.println("Connection closed");
                    break;
                } catch (Exception e) {
                    System.err.println("Error processing message: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void handleTradeMessage(JsonNode json, DataOutputStream out) throws IOException {
        String action = json.get("action").asText();
        int ticket = json.get("ticket").asInt();
        
        if ("OPEN".equals(action)) {
            String symbol = json.get("symbol").asText();
            String operation = json.get("operation").asText();
            double volume = json.get("volume").asDouble();
            double price = json.get("price").asDouble();
            long timestamp = json.get("timestamp").asLong();
            
            // Invert the operation for hedging
            String hedgeOperation = "BUY".equals(operation) ? "SELL" : "BUY";
            
            System.out.println("  Action: OPEN HEDGE");
            System.out.println("  Original: " + operation + " " + volume + " " + symbol + " @ " + price);
            System.out.println("  Hedge: " + hedgeOperation + " " + volume + " " + symbol + " @ " + price);
            
            // Store the hedge position
            positions.put(ticket, new HedgePosition(symbol, hedgeOperation, volume, price, timestamp));
            
            // Send confirmation back
            String confirmation = String.format(
                "{\"type\":\"HEDGE_CONFIRM\",\"originalTicket\":%d,\"hedgeTicket\":%d,\"symbol\":\"%s\",\"operation\":\"%s\",\"volume\":%.2f,\"status\":\"HEDGED\",\"timestamp\":%d}",
                ticket, ticket + 10000, symbol, hedgeOperation, volume, System.currentTimeMillis()
            );
            
            sendFrame(out, (byte) 0x03, confirmation);  // ACK frame type
            System.out.println("  Status: HEDGE PLACED (ticket: " + (ticket + 10000) + ")");
            System.out.println("  Confirmation sent back to EA");
            
        } else if ("CLOSE".equals(action)) {
            HedgePosition position = positions.get(ticket);
            if (position != null) {
                System.out.println("  Action: CLOSE HEDGE");
                System.out.println("  Closing hedge for ticket: " + ticket);
                System.out.println("  Position: " + position.operation + " " + position.volume + " " + position.symbol);
                
                positions.remove(ticket);
                
                // Send confirmation
                String confirmation = String.format(
                    "{\"type\":\"HEDGE_CLOSE_CONFIRM\",\"originalTicket\":%d,\"hedgeTicket\":%d,\"status\":\"CLOSED\",\"timestamp\":%d}",
                    ticket, ticket + 10000, System.currentTimeMillis()
                );
                
                sendFrame(out, (byte) 0x03, confirmation);
                System.out.println("  Status: HEDGE CLOSED");
                System.out.println("  Confirmation sent back to EA");
            } else {
                System.out.println("  Warning: No hedge position found for ticket " + ticket);
            }
        }
    }
    
    private static void handleStatusMessage(JsonNode json) {
        System.out.println("  EA Account Status Update:");
        System.out.println("    Balance: $" + json.get("balance").asDouble());
        System.out.println("    Equity: $" + json.get("equity").asDouble());
        System.out.println("    Margin: $" + json.get("margin").asDouble());
        System.out.println("    Free Margin: $" + json.get("freeMargin").asDouble());
        
        // Display current hedge positions
        if (!positions.isEmpty()) {
            System.out.println("\n  Active Hedge Positions:");
            for (Map.Entry<Integer, HedgePosition> entry : positions.entrySet()) {
                HedgePosition pos = entry.getValue();
                System.out.println("    Ticket " + entry.getKey() + ": " + 
                    pos.operation + " " + pos.volume + " " + pos.symbol + " @ " + pos.openPrice);
            }
        } else {
            System.out.println("  No active hedge positions");
        }
    }
    
    private static void sendFrame(DataOutputStream out, byte frameType, String message) throws IOException {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(messageBytes.length + 1);
        frame.put(frameType);
        frame.put(messageBytes);
        
        byte[] frameData = frame.array();
        out.writeInt(frameData.length);
        out.write(frameData);
        out.flush();
    }
    
    private static String readFrame(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length > 0 && length < 65536) {
            byte[] data = new byte[length];
            in.readFully(data);
            return new String(data, StandardCharsets.UTF_8);
        }
        return "";
    }
}