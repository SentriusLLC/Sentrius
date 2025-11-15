FROM quay.io/keycloak/keycloak:24.0.1 as builder

# Declare the argument
ARG INCLUDE_DEV_CERTS=false
ARG SENTRIUS_API_CLIENT_SECRET
ARG SENTRIUS_APROXY_CLIENT_SECRET
ARG SENTRIUS_LAUNCHER_CLIENT_SECRET
ARG JAVA_AGENTS_CLIENT_SECRET
ARG AI_AGENT_ASSESSOR_CLIENT_SECRET
ARG SENTRIUS_RDPPROXY_CLIENT_SECRET
ARG PROMPT_ADVISOR_CLIENT_SECRET

ENV SENTRIUS_API_CLIENT_SECRET=$SENTRIUS_API_CLIENT_SECRET
ENV SENTRIUS_APROXY_CLIENT_SECRET=$SENTRIUS_APROXY_CLIENT_SECRET
ENV SENTRIUS_LAUNCHER_CLIENT_SECRET=$SENTRIUS_LAUNCHER_CLIENT_SECRET
ENV JAVA_AGENTS_CLIENT_SECRET=$JAVA_AGENTS_CLIENT_SECRET
ENV AI_AGENT_ASSESSOR_CLIENT_SECRET=$AI_AGENT_ASSESSOR_CLIENT_SECRET
ENV SENTRIUS_RDPPROXY_CLIENT_SECRET=$SENTRIUS_RDPPROXY_CLIENT_SECRET
ENV PROMPT_ADVISOR_CLIENT_SECRET=$PROMPT_ADVISOR_CLIENT_SECRET


# Set environment so you can use in RUN
ENV INCLUDE_DEV_CERTS=${INCLUDE_DEV_CERTS}

# Enable health and metrics support
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
# Configure a database vendor
ENV KC_DB=postgres

WORKDIR /opt/keycloak

# Copy certs if needed

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:24.0.1
COPY --from=builder /opt/keycloak/ /opt/keycloak/

# Copy realm template and processing script
COPY ./realms/sentrius-realm.json.template /opt/keycloak/data/import/sentrius-realm.json.template
COPY ./process-realm-template.sh /opt/keycloak/bin/process-realm-template.sh
COPY ./startup.sh /opt/keycloak/bin/startup.sh

# Make scripts executable and ensure data directory is writable
USER root
RUN chmod +x /opt/keycloak/bin/process-realm-template.sh && \
    chmod +x /opt/keycloak/bin/startup.sh && \
    chown -R keycloak:keycloak /opt/keycloak/data
USER keycloak

RUN ls -l /opt/keycloak/data/import/sentrius-realm.json.template

ENTRYPOINT ["/opt/keycloak/bin/startup.sh"]
