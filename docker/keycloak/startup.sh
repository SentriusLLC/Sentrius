#!/bin/bash
echo "Starting Keycloak with dynamic realm processing..."

# Process the realm template
/opt/keycloak/bin/process-realm-template.sh

# Start Keycloak with the processed realm
exec /opt/keycloak/bin/kc.sh start-dev --proxy=edge --import-realm --import-realm-overwrite=true --health-enabled=true