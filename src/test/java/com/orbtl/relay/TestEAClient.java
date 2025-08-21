package com.orbtl.relay;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test client that simulates an Expert Advisor placing trades
 */
public class TestEAClient {
    
    private static final String API_KEY = "orbk_wzy-zKMeo3BpduPdIyWQcjWbofsOSS1n2WabsU1QH50";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    
    public static void main(String[] args) throws Exception {
        // Connection settings - use environment variables or defaults
        String host = System.getenv("RELAY_HOST") != null ? System.getenv("RELAY_HOST") : "localhost";
        int port = System.getenv("RELAY_PORT") != null ? Integer.parseInt(System.getenv("RELAY_PORT")) : 9090;
        
        System.out.println("=== Expert Advisor Client ===");
        System.out.println("Connecting to Orbtl Relay at " + host + ":" + port + "...");
        
        try (Socket socket = new Socket(host, port)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            
            // Authenticate
            String authJson = String.format(
                "{\"apiKey\":\"%s\",\"mode\":\"DIRECT\",\"mtAccount\":\"EA_12345\",\"brokerName\":\"TestBroker\"}",
                API_KEY
            );
            
            sendFrame(out, (byte) 0x01, authJson);
            System.out.println("Sent auth request...");
            
            // Read auth response
            String authResponse = readFrame(in);
            System.out.println("Auth Response: " + authResponse);
            
            if (!authResponse.contains("AUTH_SUCCESS")) {
                System.err.println("Authentication failed!");
                return;
            }
            
            System.out.println("Authentication successful!");
            System.out.println("----------------------------------------");
            
            // Start message receiver thread
            Thread receiver = new Thread(() -> {
                try {
                    while (running.get()) {
                        String message = readFrame(in);
                        if (message != null && !message.isEmpty()) {
                            System.out.println("\n[RECEIVED ACK] " + message);
                            System.out.print("Enter command (BUY/SELL/CLOSE/STATUS/QUIT): ");
                        }
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        System.err.println("Receiver error: " + e.getMessage());
                    }
                }
            });
            receiver.start();
            
            // Interactive command loop
            Scanner scanner = new Scanner(System.in);
            System.out.println("Commands: BUY, SELL, CLOSE <ticket>, STATUS, QUIT");
            System.out.println("Example: BUY EURUSD 0.1");
            System.out.println("----------------------------------------");
            
            int ticketCounter = 1000;
            
            while (running.get()) {
                System.out.print("Enter command: ");
                String command = scanner.nextLine().trim().toUpperCase();
                
                if (command.equals("QUIT")) {
                    break;
                }
                
                String message = null;
                String[] parts = command.split(" ");
                
                switch (parts[0]) {
                    case "BUY":
                    case "SELL":
                        if (parts.length >= 3) {
                            String symbol = parts[1];
                            String volume = parts[2];
                            int ticket = ticketCounter++;
                            message = String.format(
                                "{\"type\":\"TRADE\",\"action\":\"OPEN\",\"ticket\":%d,\"symbol\":\"%s\",\"operation\":\"%s\",\"volume\":%s,\"price\":1.1234,\"timestamp\":%d}",
                                ticket, symbol, parts[0], volume, System.currentTimeMillis()
                            );
                            System.out.println("Placing " + parts[0] + " order: " + symbol + " " + volume + " lots (ticket: " + ticket + ")");
                        } else {
                            System.out.println("Usage: BUY/SELL <symbol> <volume>");
                            continue;
                        }
                        break;
                        
                    case "CLOSE":
                        if (parts.length >= 2) {
                            String ticket = parts[1];
                            message = String.format(
                                "{\"type\":\"TRADE\",\"action\":\"CLOSE\",\"ticket\":%s,\"timestamp\":%d}",
                                ticket, System.currentTimeMillis()
                            );
                            System.out.println("Closing position: " + ticket);
                        } else {
                            System.out.println("Usage: CLOSE <ticket>");
                            continue;
                        }
                        break;
                        
                    case "STATUS":
                        message = String.format(
                            "{\"type\":\"STATUS\",\"balance\":10000.00,\"equity\":10500.00,\"margin\":1000.00,\"freeMargin\":9500.00,\"timestamp\":%d}",
                            System.currentTimeMillis()
                        );
                        System.out.println("Sending account status...");
                        break;
                        
                    default:
                        System.out.println("Unknown command: " + parts[0]);
                        continue;
                }
                
                if (message != null) {
                    sendFrame(out, (byte) 0x02, message);  // ROUTE_HEDGE frame type
                    System.out.println("Message sent to hedge system");
                }
            }
            
            running.set(false);
            System.out.println("Shutting down EA client...");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
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
        try {
            if (in.available() > 0 || true) {  // Always try to read
                int length = in.readInt();
                if (length > 0 && length < 65536) {  // Sanity check
                    byte[] data = new byte[length];
                    in.readFully(data);
                    return new String(data, StandardCharsets.UTF_8);
                }
            }
        } catch (EOFException e) {
            // Connection closed
            return null;
        }
        return "";
    }
}