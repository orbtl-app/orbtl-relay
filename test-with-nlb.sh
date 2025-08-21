#!/bin/bash

# Test clients connection script for Orbtl Relay NLB

NLB_ENDPOINT="k8s-wlthai-orbtlrel-0c4c22fc70-d400aa79ae178c75.elb.us-east-2.amazonaws.com"
NLB_PORT=9090

echo "=== Orbtl Relay NLB Test Client Launcher ==="
echo "NLB Endpoint: $NLB_ENDPOINT:$NLB_PORT"
echo ""

# Function to test connectivity
test_connection() {
    echo "Testing connectivity to NLB..."
    nc -zv $NLB_ENDPOINT $NLB_PORT 2>&1
    if [ $? -eq 0 ]; then
        echo "✓ Successfully connected to Orbtl Relay NLB"
        return 0
    else
        echo "✗ Failed to connect to Orbtl Relay NLB"
        echo "The NLB may still be provisioning. Please wait a few minutes and try again."
        return 1
    fi
}

# Function to run EA client with NLB
run_ea_client() {
    echo "Starting EA Client with NLB connection..."
    export RELAY_HOST=$NLB_ENDPOINT
    export RELAY_PORT=$NLB_PORT
    
    cd /Users/mbraatz/Orbtl/dev/orbtl-relay
    
    # Compile if needed
    if [ ! -f "target/test-classes/com/orbtl/relay/TestEAClient.class" ]; then
        echo "Compiling test clients..."
        mvn compile test-compile
    fi
    
    java -cp "target/classes:target/test-classes" com.orbtl.relay.TestEAClient
}

# Function to run Hedge client with NLB
run_hedge_client() {
    echo "Starting Hedge Client with NLB connection..."
    export RELAY_HOST=$NLB_ENDPOINT
    export RELAY_PORT=$NLB_PORT
    
    cd /Users/mbraatz/Orbtl/dev/orbtl-relay
    
    # Compile if needed
    if [ ! -f "target/test-classes/com/orbtl/relay/TestHedgeClient.class" ]; then
        echo "Compiling test clients..."
        mvn compile test-compile
    fi
    
    java -cp "target/classes:target/test-classes:target/dependency/*" com.orbtl.relay.TestHedgeClient
}

# Menu
echo "Select an option:"
echo "1) Test NLB connectivity"
echo "2) Run EA Client via NLB"
echo "3) Run Hedge Client via NLB"
echo "4) Check NLB status"
echo ""

read -p "Enter your choice (1-4): " choice

case $choice in
    1)
        test_connection
        ;;
    2)
        if test_connection; then
            echo ""
            run_ea_client
        fi
        ;;
    3)
        if test_connection; then
            echo ""
            run_hedge_client
        fi
        ;;
    4)
        echo "Checking NLB status..."
        kubectl get service orbtl-relay-nlb -n wlth-ai
        echo ""
        echo "NLB Details:"
        kubectl describe service orbtl-relay-nlb -n wlth-ai | grep -E "(LoadBalancer Ingress|Port:|Endpoints:)"
        ;;
    *)
        echo "Invalid option"
        exit 1
        ;;
esac