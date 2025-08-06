#!/bin/bash

# Sentrius SSH Proxy Demo Script
# This script demonstrates the SSH proxy functionality

echo "=== Sentrius SSH Proxy Server Demo ==="
echo "This script shows how the SSH proxy applies safeguards to SSH commands"
echo ""

# Start SSH proxy in background
echo "Starting SSH Proxy Server..."
cd /home/runner/work/Sentrius/Sentrius/ssh-proxy

# Create a simple test to show trigger responses
echo "Testing trigger response formatting..."

# Test the terminal response service
mvn exec:java -Dexec.mainClass="io.sentrius.sso.sshproxy.SshProxyApplication" \
    -Dexec.args="--spring.profiles.active=demo" \
    -Dsentrius.ssh-proxy.port=2222 \
    -Dsentrius.ssh-proxy.enabled=true &

SSH_PID=$!

echo "SSH Proxy started with PID: $SSH_PID"
echo "You can now connect with: ssh -p 2222 testuser@localhost"
echo ""
echo "Try these commands to see safeguards in action:"
echo "  - 'sudo rm -rf /' (will be BLOCKED)"
echo "  - 'sudo ls' (will show WARNING)"  
echo "  - 'ls' (will be allowed)"
echo "  - 'help' (shows built-in commands)"
echo "  - 'exit' (closes session)"
echo ""
echo "Press Ctrl+C to stop the demo"

# Wait for user to stop
trap "kill $SSH_PID 2>/dev/null; echo 'SSH Proxy stopped'; exit 0" INT
wait