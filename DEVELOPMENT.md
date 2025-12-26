# Development Guide

This guide covers development workflows, building, and testing Sentrius.

## Table of Contents

- [Project Structure](#project-structure)
- [Building](#building)
- [Testing](#testing)
- [Development Workflow](#development-workflow)
- [Contributing](#contributing)

## Project Structure

Sentrius consists of multiple Maven sub-projects:

```
sentrius/
├── core/                    # Core business logic and SSH session management
├── api/                     # REST API layer and web interface
├── dataplane/              # Secure data transfer and processing
├── llm-core/               # Language model integration core
├── llm-dataplane/          # LLM data processing layer
├── integration-proxy/      # LLM proxy service for AI integration
├── agent-proxy/            # Agent communication proxy
├── analytics/              # Java-based monitoring agent
├── ai-agent/               # Intelligent monitoring and automation agent
├── agent-launcher/         # Dynamic agent lifecycle management
├── provenance-core/        # Event tracking and audit framework
├── provenance-ingestor/    # Event ingestion and processing
├── python-agent/           # Python-based agent framework
├── ops-scripts/            # Operational scripts for deployment
├── sentrius-chart/         # Helm chart for full deployment
├── sentrius-chart-launcher/# Helm chart for launcher service
└── pom.xml                 # Root Maven POM
```

### Core Module

Contains business logic, including:
- Enclave management
- Zero trust policy enforcement
- Secure SSH connection handling

### API Module

A RESTful interface for interacting with the core functionalities. The API module exposes endpoints that let you:
- Create and manage enclaves
- Configure security rules
- Visualize SSH sessions and logs
- Handle user access and authentication

## Building

### Prerequisites

- **Java 17** or later
- **Apache Maven 3.6+**

### Full Build

Build the entire project including all modules:

```bash
mvn clean install
```

**Build Performance:**
- Initial build: ~7 minutes (downloads dependencies)
- Subsequent builds: 3-5 minutes (cached dependencies)
- Test execution: ~1 minute

### Build Without Tests

To speed up builds during development:

```bash
mvn clean install -DskipTests
```

### Build Specific Modules

Build only specific modules with dependencies:

```bash
# Build core modules
mvn clean install -pl core,api,dataplane -am

# Build specific module with dependencies
mvn clean install -pl api -am
```

### Maven Warnings

The build produces these warnings which are **expected and safe to ignore**:

```
'dependencyManagement.dependencies.dependency' must be unique: org.projectlombok:lombok:jar
'dependencyManagement.dependencies.dependency' must be unique: org.springframework.boot:spring-boot-starter-web:jar
'dependencies.dependency' must be unique: org.springframework.boot:spring-boot-starter-actuator:jar
```

## Testing

### Running Tests

Run all tests:

```bash
mvn test
```

Run tests for specific module:

```bash
cd api
mvn test
```

### CI/CD Testing

Sentrius includes comprehensive CI/CD testing:

- **Automated testing** runs on every push and pull request via GitHub Actions
- **Helm chart validation** including linting, template rendering, and schema validation
- **Integration testing** with Kubernetes clusters for deployment validation

### Local Helm Chart Testing

Test Helm charts locally before deployment:

```bash
# Test all charts
./ops-scripts/test-helm-charts.sh

# Test specific aspects
./ops-scripts/test-helm-charts.sh lint      # Lint charts
./ops-scripts/test-helm-charts.sh template  # Test rendering
./ops-scripts/test-helm-charts.sh config    # Test configurations
```

For detailed testing documentation, see [docs/helm-testing.md](docs/helm-testing.md).

## Development Workflow

### Setting Up Development Environment

1. **Clone the repository:**
   ```bash
   git clone https://github.com/SentriusLLC/Sentrius-private.git
   cd Sentrius-private
   ```

2. **Build the project:**
   ```bash
   mvn clean install -DskipTests
   ```

3. **Set up required services:**
   - PostgreSQL database
   - Keycloak authentication server
   - OpenTelemetry endpoint (optional for development)

4. **Configure application properties:**
   - Copy `application.properties.example` to `application.properties`
   - Update database and Keycloak connection settings

### Running in Development Mode

#### Using the Convenience Script

```bash
./ops-scripts/local/run-sentrius.sh --build
```

#### Manual Start

```bash
cd api
mvn spring-boot:run
```

#### With Custom Configuration

```bash
export KEYCLOAK_BASE_URL=http://localhost:8180
export DATABASE_PASSWORD=password
export KEYSTORE_PASSWORD=keystorepassword
cd api
mvn spring-boot:run
```

### Docker Image Development

Build Docker images for testing:

```bash
# Build all images sequentially
./ops-scripts/base/build-images.sh --all --no-cache

# Build all images concurrently (faster)
./ops-scripts/base/build-all-images-concurrent.sh --all --no-cache

# Build specific images
./ops-scripts/base/build-images.sh --sentrius --sentrius-keycloak
```

Build with development certificates:

```bash
./ops-scripts/base/build-images.sh --all --include-dev-certs
```

### Python Agent Development

Python agents require Python 3.12+ and dependencies:

```bash
cd python-agent

# Install dependencies
pip3 install -r requirements.txt

# Test mode (no external services required)
TEST_MODE=true python3 main.py chat-helper --task-data '{"test": "message"}'

# Production mode
python3 main.py chat-helper --config application.properties
```

See [CUSTOM_AGENTS.md](CUSTOM_AGENTS.md) for detailed agent development guide.

## Contributing

### Getting Started

1. Fork the repository
2. Create a feature branch for your changes
3. Make your changes following the coding standards
4. Write tests for your changes
5. Run the full test suite
6. Open a pull request with a clear description

### Coding Standards

- Follow existing code style and patterns
- Write meaningful commit messages
- Add tests for new functionality
- Update documentation as needed
- Keep changes focused and minimal

### Pull Request Process

1. Ensure all tests pass
2. Update documentation if needed
3. Add a clear description of changes
4. Link to relevant issues
5. Wait for code review
6. Address review feedback

### Reporting Issues

If you encounter any issues or have requests:

1. Check existing issues first
2. Provide clear reproduction steps
3. Include relevant logs and error messages
4. Specify your environment (OS, Java version, etc.)

## Development Tips

### IDE Setup

**IntelliJ IDEA:**
- Import as Maven project
- Enable annotation processing for Lombok
- Configure Java 17 SDK

**Eclipse:**
- Import as Existing Maven Project
- Install Lombok plugin
- Set compiler compliance to Java 17

### Debugging

**Local Debugging:**
```bash
cd api
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

Then attach your IDE debugger to port 5005.

**Kubernetes Debugging:**
```bash
kubectl port-forward -n dev pod/<pod-name> 5005:5005
```

### Hot Reload

Spring Boot DevTools is included for automatic restart on code changes:

```bash
cd api
mvn spring-boot:run
```

Changes to Java classes will trigger automatic restart.

## Performance Expectations

| Operation | Time | Notes |
|-----------|------|-------|
| Maven build (clean install) | 7m24s | First build, downloads dependencies |
| Maven build (cached) | 3-5m | Subsequent builds |
| Maven test execution | 1m3s | Full test suite |
| Docker image build | 5-10m | All images, sequential |
| Docker image build (concurrent) | 3-7m | All images, parallel |
| Python dependency install | <1m | Initial setup |

## Next Steps

- Review [DEPLOYMENT.md](DEPLOYMENT.md) for deployment options
- See [INTEGRATIONS.md](INTEGRATIONS.md) for external service integrations
- Check [CUSTOM_AGENTS.md](CUSTOM_AGENTS.md) for creating custom agents
- Read [API_DOCUMENTATION.md](docs/api-documentation.md) for API reference
