# Orbtl Relay

High-performance TCP communication service for Expert Advisor (EA) to EA communication through Orbtl's cloud infrastructure.

## Features

- Pure TCP socket server for low-latency EA connections
- Pass-through message routing (no parsing overhead)
- Redis pub/sub for scalable message distribution
- API key authentication with monolith integration
- Support for both Trade Copier and direct connection modes
- Binary protocol for efficient message transfer

## Architecture

```
EA1 (MT4/5) → TCP:9090 → Orbtl Relay → Redis → Orbtl Relay → TCP:9090 → EA2 (MT4/5)
                              ↓
                         Monolith (Auth)
```

## Quick Start

### Prerequisites

- Java 17+
- Redis running on localhost:6379
- Monolith service running on localhost:8080

### Running Locally

```bash
# Build the project
./mvnw clean package

# Run the service
./mvnw spring-boot:run
```

### Running with Docker

```bash
# Build and start services
docker-compose up -d

# View logs
docker-compose logs -f orbtl-relay

# Stop services
docker-compose down
```

## Configuration

Key configuration in `application.yml`:

```yaml
relay:
  tcp:
    port: 9090              # TCP port for EA connections
    max-connections: 1000   # Maximum concurrent connections
    heartbeat-interval: 30s # Heartbeat check interval
    auth-timeout: 5s        # Authentication timeout
  
  auth:
    monolith-url: http://localhost:8080  # Monolith service URL
```

## MQL5 Client Usage

### Installation

1. Copy `mql5-client/OrbtlRelay.mqh` to your MT5 `Include` folder
2. Copy `mql5-client/TestEA.mq5` to your MT5 `Experts` folder

### Basic Usage

```mql5
#include <OrbtlRelay.mqh>

input string ApiKey = "your-api-key";
input CONNECTION_MODE Mode = MODE_DIRECT;

OrbtlRelay relay;

int OnInit() {
    if (!relay.Initialize(ApiKey, Mode)) {
        return INIT_FAILED;
    }
    return INIT_SUCCEEDED;
}

void SendHedgeRequest() {
    string message = "{\"type\":\"OPEN\",\"symbol\":\"EURUSD\",\"lots\":0.1}";
    relay.SendHedgeMessage(message);
}
```

## Protocol

### Frame Structure

```
[Length: 4 bytes] [Type: 1 byte] [Payload: Variable]
```

### Frame Types

- `0x01` AUTH - Authentication request
- `0x02` ROUTE_HEDGE - Route to hedge account
- `0x03` ROUTE_CHALLENGE - Route to challenge
- `0x04` BROADCAST - Broadcast to group
- `0x05` HEARTBEAT - Keep-alive
- `0x10` AUTH_SUCCESS - Authentication successful
- `0x11` AUTH_FAILED - Authentication failed

## Redis Channels

- `challenge:{id}:ack` - Challenge-specific acknowledgments
- `hedge:account:{id}` - Hedge account messages
- `ea:broadcast:{userId}` - User broadcast messages

## Testing

```bash
# Run tests
./mvnw test

# Run with specific profile
./mvnw test -Dspring.profiles.active=test
```

## Monitoring

Health check endpoint: `http://localhost:8091/actuator/health`

Metrics endpoint: `http://localhost:8091/actuator/metrics`

## AWS Deployment

For production deployment on AWS:

1. Use Network Load Balancer (NLB) for TCP traffic
2. Configure security groups for port 9090
3. Use ElastiCache Redis for message broker
4. Deploy as EKS service with appropriate scaling

## License

Copyright 2024 Orbtl Inc.