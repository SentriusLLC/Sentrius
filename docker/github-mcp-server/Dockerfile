# GitHub MCP Server Docker Image
# This builds the github-mcp-server from source using Go
FROM golang:latest AS build
ARG VERSION="dev"

# Set the working directory
WORKDIR /build

# Clone the github-mcp-server repository
RUN git clone https://github.com/github/github-mcp-server.git .

# Download all dependencies
RUN go mod download

# Build the server binary
RUN CGO_ENABLED=0 go build \
    -ldflags="-s -w -X main.version=${VERSION} -X main.commit=$(git rev-parse HEAD) -X main.date=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    -o /bin/github-mcp-server \
    cmd/github-mcp-server/main.go

# Runtime stage - use minimal distroless image
FROM gcr.io/distroless/base-debian12

# Add required MCP server annotation
LABEL io.modelcontextprotocol.server.name="io.github.github/github-mcp-server"

# Set the working directory
WORKDIR /server

# Copy the binary from the build stage
COPY --from=build /bin/github-mcp-server .

# Environment variables for GitHub token will be passed at runtime
ENV GITHUB_PERSONAL_ACCESS_TOKEN=""

# Set the entrypoint to the server binary
ENTRYPOINT ["/server/github-mcp-server"]

# Default arguments for ENTRYPOINT (stdio mode)
CMD ["stdio"]
