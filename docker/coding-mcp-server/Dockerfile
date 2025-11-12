# Coding MCP Server Docker Image
FROM python:3.12-slim

# Install git (required for repository operations via GitHub MCP)
RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy Python agent code
COPY python-agent /app

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Expose the port for MCP server
EXPOSE 3000

# Environment variables (will be passed at runtime)
ENV GITHUB_TOKEN_ID=""
ENV LLM_PROXY_URL="http://localhost:8084"
ENV INTEGRATION_PROXY_URL="http://localhost:8080"
ENV INSTANCE_ID="default"
ENV MCP_SERVER_MODE="http"
ENV KEYCLOAK_BASE_URL="http://localhost:8180"
ENV KEYCLOAK_CLIENT_ID="python-agents"
ENV KEYCLOAK_CLIENT_SECRET=""

# Run the MCP server
CMD ["python3", "mcp_server.py"]
