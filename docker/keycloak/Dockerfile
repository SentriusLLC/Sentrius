FROM quay.io/keycloak/keycloak:24.0.1 as builder

# Enable health and metrics support
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

# Configure a database vendor
ENV KC_DB=postgres

WORKDIR /opt/keycloak

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:24.0.1
COPY --from=builder /opt/keycloak/ /opt/keycloak/

# Install gettext for envsubst command
USER root
RUN microdnf install -y gettext && microdnf clean all
USER keycloak

# Copy realm template and processing script
COPY ./realms/sentrius-realm.json.template /opt/keycloak/data/import/sentrius-realm.json.template
COPY ./process-realm-template.sh /opt/keycloak/bin/process-realm-template.sh

# Make the script executable
USER root
RUN chmod +x /opt/keycloak/bin/process-realm-template.sh
USER keycloak

RUN ls -l /opt/keycloak/data/import/sentrius-realm.json.template

# Create startup script that processes template then starts Keycloak
COPY <<'EOF' /opt/keycloak/bin/startup.sh
#!/bin/bash
echo "Starting Keycloak with dynamic realm processing..."

# Process the realm template
/opt/keycloak/bin/process-realm-template.sh

# Start Keycloak with the processed realm
exec /opt/keycloak/bin/kc.sh start-dev --proxy=passthrough --import-realm --import-realm-overwrite=true --health-enabled=true
EOF

USER root
RUN chmod +x /opt/keycloak/bin/startup.sh
USER keycloak

ENTRYPOINT ["/opt/keycloak/bin/startup.sh"]

