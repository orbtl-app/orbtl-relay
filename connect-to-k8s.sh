#!/bin/bash

# Script to connect test clients to Orbtl Relay in Kubernetes

echo "=== Orbtl Relay Connection Helper ==="
echo ""

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo "kubectl is not installed or not in PATH"
    exit 1
fi

# Check current context
CURRENT_CONTEXT=$(kubectl config current-context)
echo "Current kubectl context: $CURRENT_CONTEXT"
echo ""

# Function to start port forwarding
start_port_forward() {
    echo "Starting port-forward to Orbtl Relay..."
    echo "This will forward localhost:9090 to the Orbtl Relay service in Kubernetes"
    echo ""
    kubectl port-forward deployment/orbtl-relay -n wlth-ai 9090:9090
}

# Function to run EA client
run_ea_client() {
    echo "Compiling and running EA Client..."
    cd /Users/mbraatz/Orbtl/dev/orbtl-relay
    javac -cp "target/classes:target/test-classes" src/test/java/com/orbtl/relay/TestEAClient.java -d target/test-classes
    java -cp "target/classes:target/test-classes" com.orbtl.relay.TestEAClient
}

# Function to run Hedge client
run_hedge_client() {
    echo "Compiling and running Hedge Client..."
    cd /Users/mbraatz/Orbtl/dev/orbtl-relay
    javac -cp "target/classes:target/test-classes:target/dependency/*" src/test/java/com/orbtl/relay/TestHedgeClient.java -d target/test-classes
    java -cp "target/classes:target/test-classes:target/dependency/*" com.orbtl.relay.TestHedgeClient
}

# Menu
echo "Select an option:"
echo "1) Start port-forward to Orbtl Relay (run this first in a separate terminal)"
echo "2) Run EA Client (simulates Expert Advisor)"
echo "3) Run Hedge Client (simulates hedge system)"
echo "4) Check Orbtl Relay pod status"
echo "5) View Orbtl Relay logs"
echo ""

read -p "Enter your choice (1-5): " choice

case $choice in
    1)
        start_port_forward
        ;;
    2)
        run_ea_client
        ;;
    3)
        run_hedge_client
        ;;
    4)
        echo "Checking Orbtl Relay pod status..."
        kubectl get pods -n wlth-ai -l app.kubernetes.io/name=orbtl-relay
        echo ""
        echo "Pod details:"
        kubectl describe pod -n wlth-ai -l app.kubernetes.io/name=orbtl-relay
        ;;
    5)
        echo "Viewing Orbtl Relay logs (last 50 lines)..."
        kubectl logs -n wlth-ai -l app.kubernetes.io/name=orbtl-relay --tail=50 -f
        ;;
    *)
        echo "Invalid option"
        exit 1
        ;;
esac