# Use an OpenJDK image as the base
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy the pre-built API JAR into the container
COPY agent.jar /app/agent.jar

# Expose the port the app runs on
EXPOSE 8080

RUN apt-get update && apt-get install -y curl


# Command to run the app
CMD ["java", "-jar", "/app/agent.jar", "--spring.config.location=/config/application.properties"]
