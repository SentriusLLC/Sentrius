# Use an OpenJDK image as the base
FROM eclipse-temurin:17-jdk-jammy

# Declare the argument
ARG INCLUDE_DEV_CERTS=false

# Set environment so you can use in RUN
ENV INCLUDE_DEV_CERTS=${INCLUDE_DEV_CERTS}


# Set working directory
WORKDIR /app

# Copy the pre-built API JAR into the container
COPY monitoring.jar /app/monitoring.jar


COPY dev-certs/sentrius-ca.crt /tmp/sentrius-ca.crt

RUN if [ "$INCLUDE_DEV_CERTS" = "true" ] && [ -f /tmp/sentrius-ca.crt ]; then \
      echo "Importing dev CA cert..." && \
      keytool -import -noprompt -trustcacerts \
        -alias sentrius-local-ca \
        -file /tmp/sentrius-ca.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" \
        -storepass changeit ; \
    else \
      echo "Skipping cert import"; \
    fi


# Expose the port the app runs on
EXPOSE 8080

# Install curl for health checks
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Command to run the application
# Note: guacd is now provided as a sidecar container using the official Docker image
CMD ["java", "-XX:+UseContainerSupport", "-jar", "/app/monitoring.jar", "--spring.config.location=/config/monitoring-agent-application.properties"]
