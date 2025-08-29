# Use an OpenJDK image as the base
FROM eclipse-temurin:17-jdk-jammy

# Declare the argument
ARG INCLUDE_DEV_CERTS=false

# Set environment so you can use in RUN
ENV INCLUDE_DEV_CERTS=${INCLUDE_DEV_CERTS}

# Set working directory
WORKDIR /app

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

# Copy the pre-built API JAR into the container
COPY sentrius.jar /app/sentrius.jar
COPY exampleInstallWithTypes.yml /app/exampleInstallWithTypes.yml
COPY demoInstaller.yml /app/demoInstaller.yml

# Expose the port the app runs on
EXPOSE 8080

RUN apt-get update && apt-get install -y curl


# Command to run the app
CMD ["java","-XX:+UseContainerSupport", "-jar", "/app/sentrius.jar", "--spring.config.location=/config/api-application.properties", "--dynamic.properties.path=/config/dynamic.properties"]
