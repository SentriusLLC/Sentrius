# GitHub MCP Server Docker Image
FROM node:20-alpine

# Install git (required for repository operations)
RUN apk add --no-cache git

# Create app directory
WORKDIR /app

# Clone and build the github-mcp-server
RUN git clone https://github.com/github/github-mcp-server.git . && \
    npm install && \
    npm run build

# Expose the port for MCP server (using stdio by default, but we'll add HTTP support)
EXPOSE 3000

# Environment variables for GitHub token will be passed at runtime
ENV GITHUB_PERSONAL_ACCESS_TOKEN=""

# Run the MCP server
CMD ["node", "dist/index.js"]
