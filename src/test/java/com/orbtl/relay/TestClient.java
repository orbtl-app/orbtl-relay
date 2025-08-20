package com.orbtl.relay;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TestClient {

    public static void main(String[] args) throws Exception {
        System.out.println("Connecting to Orbtl Relay...");

        try (Socket socket = new Socket("localhost", 9090)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Create auth JSON
            String authJson = String.format(
                "{\"apiKey\":\"%s\",\"mode\":\"DIRECT\",\"mtAccount\":\"12345\",\"brokerName\":\"TestBroker\"}",
                "orbk_wzy-zKMeo3BpduPdIyWQcjWbofsOSS1n2WabsU1QH50"  // Replace with a valid API key
            );

            // Build auth frame
            byte[] authBytes = authJson.getBytes(StandardCharsets.UTF_8);
            ByteBuffer frame = ByteBuffer.allocate(authBytes.length + 1);
            frame.put((byte) 0x01);  // AUTH frame type
            frame.put(authBytes);

            // Send with length prefix
            byte[] frameData = frame.array();
            out.writeInt(frameData.length);  // 4-byte length
            out.write(frameData);
            out.flush();

            System.out.println("Sent auth request, waiting for response...");

            // Read response
            int responseLength = in.readInt();
            byte[] response = new byte[responseLength];
            in.readFully(response);

            String responseStr = new String(response, StandardCharsets.UTF_8);
            System.out.println("Response: " + responseStr);

            // If authenticated, send a test message
            if (responseStr.contains("AUTH_SUCCESS")) {
                System.out.println("Authentication successful!");

                // Send a hedge message
                String hedgeMessage = "{\"type\":\"TEST\",\"data\":\"Hello from test client\"}";
                byte[] hedgeBytes = hedgeMessage.getBytes(StandardCharsets.UTF_8);

                ByteBuffer hedgeFrame = ByteBuffer.allocate(hedgeBytes.length + 1);
                hedgeFrame.put((byte) 0x02);  // ROUTE_HEDGE frame type
                hedgeFrame.put(hedgeBytes);

                byte[] hedgeFrameData = hedgeFrame.array();
                out.writeInt(hedgeFrameData.length);
                out.write(hedgeFrameData);
                out.flush();

                System.out.println("Sent hedge message");

                // Keep connection open to receive messages
                System.out.println("Listening for messages (press Ctrl+C to stop)...");
                while (true) {
                    int msgLength = in.readInt();
                    byte[] msg = new byte[msgLength];
                    in.readFully(msg);
                    System.out.println("Received: " + new String(msg, StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}