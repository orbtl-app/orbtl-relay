# Orbtl Relay Deployment Guide

## Current Deployment Status

The Orbtl Relay is deployed to the Kubernetes cluster in the `wlth-ai` namespace.

### Service Status
- **Pod**: Running (1/1 Ready)
- **TCP Port**: 9090 (for EA/Hedge connections)
- **HTTP Port**: 8091 (health checks and metrics)
- **Load Balancer**: ✅ Active
- **NLB Endpoint**: `k8s-wlthai-orbtlrel-0c4c22fc70-d400aa79ae178c75.elb.us-east-2.amazonaws.com:9090`

## Connecting Test Clients

The Network Load Balancer (NLB) is now active and ready for connections.

### Option 1: Using the Connection Script

```bash
# Run the connection helper script
./connect-to-k8s.sh

# Choose option 1 to start port forwarding (run in one terminal)
# Choose option 2 to run EA client (run in another terminal)  
# Choose option 3 to run Hedge client (run in third terminal)
```

### Option 2: Manual Connection

#### Step 1: Start Port Forwarding
In one terminal window:
```bash
kubectl port-forward deployment/orbtl-relay -n wlth-ai 9090:9090
```

#### Step 2: Run EA Client
In another terminal:
```bash
cd /Users/mbraatz/Orbtl/dev/orbtl-relay
mvn compile test-compile
java -cp "target/classes:target/test-classes" com.orbtl.relay.TestEAClient
```

#### Step 3: Run Hedge Client
In a third terminal:
```bash
cd /Users/mbraatz/Orbtl/dev/orbtl-relay
mvn compile test-compile  
java -cp "target/classes:target/test-classes:target/dependency/*" com.orbtl.relay.TestHedgeClient
```

### Option 3: Direct Connection via NLB

Connect directly to the NLB endpoint:

```bash
# Use the NLB test script
./test-with-nlb.sh

# Or manually set environment variables
export RELAY_HOST=k8s-wlthai-orbtlrel-0c4c22fc70-d400aa79ae178c75.elb.us-east-2.amazonaws.com
export RELAY_PORT=9090

# Run clients
java -cp "target/classes:target/test-classes" com.orbtl.relay.TestEAClient
```

## Monitoring

### View Logs
```bash
kubectl logs -f deployment/orbtl-relay -n wlth-ai
```

### Check Pod Status
```bash
kubectl get pods -n wlth-ai -l app.kubernetes.io/name=orbtl-relay
```

### Describe Pod
```bash
kubectl describe pod -n wlth-ai -l app.kubernetes.io/name=orbtl-relay
```

## AWS Configuration

### IAM Roles
- **Dev Role**: `orbtl-relay-dev-irsa-role`
- **Prod Role**: `orbtl-relay-prod-irsa-role`

### Service Accounts
- **Dev**: `orbtl-relay-dev` (namespace: wlth-ai)
- **Prod**: `orbtl-relay-prod` (namespace: wlth-ai)

### Parameter Store Paths
- **Dev**: `/config/dev/relay/`
- **Prod**: `/config/prod/relay/`

### Secrets Manager
- **Dev**: `orbtl-relay-dev-secrets`
- **Prod**: `orbtl-relay-prod-secrets`

## Troubleshooting

### NLB Successfully Created
The Network Load Balancer has been successfully provisioned and is accessible at:
- **Endpoint**: `k8s-wlthai-orbtlrel-0c4c22fc70-d400aa79ae178c75.elb.us-east-2.amazonaws.com`
- **Port**: `9090`

### Connection Refused
If you get "Connection refused" when trying to connect:
1. Check that port-forward is running
2. Verify the pod is running: `kubectl get pods -n wlth-ai`
3. Check logs for errors: `kubectl logs deployment/orbtl-relay -n wlth-ai`

### Authentication Failed
If authentication fails:
1. Verify the API key is correct in test clients
2. Check that the API key exists in the application configuration
3. Review logs for authentication errors