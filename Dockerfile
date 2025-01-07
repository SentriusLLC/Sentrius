# Use an OpenJDK image as the base
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy the pre-built API JAR into the container
COPY api/target/sentrius-api-1.0.0-SNAPSHOT.jar /app/sentrius.jar
COPY docker/sentrius/exampleInstallWithTypes.yml /app/exampleInstallWithTypes.yml

# Expose the port the app runs on
EXPOSE 8080

RUN apt-get update && apt-get install -y curl


# Command to run the app
CMD ["java", "-jar", "/app/sentrius.jar", "--spring.config.location=/config/api-application.properties", "--dynamic.properties.path=/config/dynamic.properties"]
