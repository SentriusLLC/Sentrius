# GitHub MCP Server Docker Image with HTTP Wrapper
# This builds the github-mcp-server from source and wraps it with an HTTP interface
FROM golang:latest AS build
ARG VERSION="dev"

# Build the github-mcp-server
WORKDIR /build/mcp-server
RUN git clone https://github.com/github/github-mcp-server.git .
RUN go mod download
RUN CGO_ENABLED=0 go build \
    -ldflags="-s -w -X main.version=${VERSION} -X main.commit=$(git rev-parse HEAD) -X main.date=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    -o /bin/github-mcp-server \
    cmd/github-mcp-server/main.go

# Build the HTTP wrapper
WORKDIR /build/wrapper
COPY go.mod .
COPY http-wrapper.go .
RUN CGO_ENABLED=0 go build -o /bin/http-wrapper http-wrapper.go

# Runtime stage - use minimal base image with shell for debugging
FROM gcr.io/distroless/base-debian12

# Add required MCP server annotation
LABEL io.modelcontextprotocol.server.name="io.github.github/github-mcp-server"

# Set the working directory
WORKDIR /server

# Copy both binaries from the build stage
COPY --from=build /bin/github-mcp-server .
COPY --from=build /bin/http-wrapper .

# Environment variables for GitHub token will be passed at runtime
ENV GITHUB_PERSONAL_ACCESS_TOKEN=""
ENV PORT=3000

# Set the entrypoint to the HTTP wrapper
ENTRYPOINT ["/server/http-wrapper"]
