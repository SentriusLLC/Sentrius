# Sentrius Zero Trust Security Platform

Sentrius is a Java-based zero trust security management system for protecting infrastructure with SSH session monitoring, LLM integration, and dynamic policy enforcement. The platform consists of 12+ Maven modules and supports both local development and Kubernetes deployment.

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### Bootstrap and Build the Repository
- **Java 17** and **Maven 3.6+** are required
- **CRITICAL**: Build takes **7 minutes 24 seconds**. NEVER CANCEL builds. Set timeout to **60+ minutes**.
- **CRITICAL**: Tests take **1 minute 3 seconds**. NEVER CANCEL tests. Set timeout to **30+ minutes**.

```bash
# Build the entire project (7m24s - NEVER CANCEL)
mvn clean install -DskipTests

# Run all tests (1m3s - NEVER CANCEL)  
mvn test

# Build including tests (8+ minutes total - NEVER CANCEL)
mvn clean install
```

### Python Agent Setup
- **Python 3.12+** required
- Install dependencies (completes in <1 minute):
```bash
cd python-agent
pip3 install -r requirements.txt
```

### Container and Kubernetes Tools
- **Docker**, **kubectl**, and **helm** are required for deployment
- Validate Helm charts (completes in seconds):
```bash
helm lint sentrius-chart
helm lint sentrius-chart-launcher
helm template test-launcher sentrius-chart-launcher --set tenant=test-tenant --dry-run
```

## Infrastructure Dependencies

### Required for Full Operation
- **PostgreSQL database** - Application will not start without it
- **Keycloak authentication server** - Required for OAuth2/JWT authentication
- **OpenTelemetry endpoint** - Required for observability

### Optional Components
- **Neo4j** - For graph-based analysis (optional)
- **Kafka** - For event streaming (optional)
- **Kubernetes cluster** - For production deployment

### Local Development Dependencies
The application expects these services by default:
- PostgreSQL at `home.guard.local:5432` (database: `sentrius`, user: `postgres`)
- Keycloak at `http://localhost:8180`
- OpenTelemetry at `http://localhost:4317`

## Build System

### Maven Warnings (Expected and Safe)
The build produces these warnings which are **expected and safe to ignore**:
```
'dependencyManagement.dependencies.dependency' must be unique: org.projectlombok:lombok:jar
'dependencyManagement.dependencies.dependency' must be unique: org.springframework.boot:spring-boot-starter-web:jar
'dependencies.dependency' must be unique: org.springframework.boot:spring-boot-starter-actuator:jar
```

### Build Performance
- **Initial build**: 7m24s (downloads dependencies)
- **Subsequent builds**: 3-5 minutes (cached dependencies)
- **Test execution**: 1m3s consistently
- **Frontend npm install**: Included in build process automatically

## Running Sentrius

### Local Development (Requires Infrastructure)
```bash
# Option 1: Using convenience script (requires PostgreSQL, Keycloak)
./ops-scripts/local/run-sentrius.sh --build

# Option 2: Direct API server execution (requires PostgreSQL, Keycloak)
cd api
mvn spring-boot:run

# Option 3: Using environment variables
export KEYCLOAK_BASE_URL=http://localhost:8180
export DATABASE_PASSWORD=password
export KEYSTORE_PASSWORD=keystorepassword
cd api
mvn spring-boot:run
```

### Kubernetes Deployment (Recommended)
```bash
# Build all Docker images sequentially (5-10 minutes - NEVER CANCEL)
./ops-scripts/base/build-images.sh --all --no-cache

# OR build all Docker images concurrently for faster builds (3-7 minutes - NEVER CANCEL)
./ops-scripts/base/build-all-images-concurrent.sh --all --no-cache

# Deploy to local Kubernetes cluster (HTTP)
./ops-scripts/local/deploy-helm.sh

# Deploy with TLS enabled
./ops-scripts/local/deploy-helm.sh --tls --install-cert-manager

# Access services (HTTP deployment)
kubectl port-forward -n dev service/sentrius-sentrius 8080:8080
kubectl port-forward -n dev service/sentrius-keycloak 8081:8081
```

### Python Agent Execution
```bash
cd python-agent

# Test mode (no external services required)
TEST_MODE=true python3 main.py chat-helper --task-data '{"test": "message"}'

# Production mode (requires Keycloak and Sentrius API)
python3 main.py chat-helper --config application.properties

# Available agents: chat-helper, mcp
python3 main.py --help
```

## Validation and Testing

### Always Validate After Changes
```bash
# Build validation (7m24s - NEVER CANCEL, timeout: 60+ minutes)
mvn clean install

# Test validation (1m3s - NEVER CANCEL, timeout: 30+ minutes)
mvn test

# Python agent validation
cd python-agent && TEST_MODE=true python3 main.py chat-helper

# Helm chart validation
helm lint sentrius-chart
helm lint sentrius-chart-launcher
```

### Manual Testing Scenarios
After making changes, **ALWAYS** test these scenarios:

1. **Java Build Validation**: Run `mvn clean install` and verify all modules build successfully
2. **Python Agent Test**: Run Python agent in test mode to verify framework works
3. **Helm Template Validation**: Ensure charts can render templates without errors
4. **Integration Test**: If infrastructure is available, test OAuth2 authentication flow

### CI/CD Validation
Before committing, ensure changes pass:
```bash
# GitHub Actions equivalent commands
mvn -B package --file pom.xml  # Maven build check
mvn test                       # Test execution  
helm lint sentrius-chart*      # Helm validation
```

## Architecture Overview

### Maven Modules (12 total)
- **provenance-core**: Event tracking and audit framework
- **core**: Core business logic and SSH session management  
- **api**: REST API layer and web interface
- **dataplane**: Secure data transfer and processing
- **llm-core**: Language model integration core
- **llm-dataplane**: LLM data processing layer
- **integration-proxy**: LLM proxy service for AI integration
- **agent-proxy**: Agent communication proxy
- **analytics**: Java-based monitoring agent
- **ai-agent**: Intelligent monitoring and automation agent
- **agent-launcher**: Dynamic agent lifecycle management
- **provenance-ingestor**: Event ingestion and processing

### Key Technologies
- **Spring Boot 3.4.x** with Java 17
- **PostgreSQL** for primary data storage
- **Keycloak** for OAuth2/OIDC authentication
- **Neo4j** for graph analysis (optional)
- **Kafka** for event streaming (optional)
- **OpenTelemetry** for observability
- **Kubernetes/Helm** for container orchestration

## Common Tasks

### Building Specific Modules
```bash
# Build only core modules
mvn clean install -pl core,api,dataplane -am

# Build specific module with dependencies
mvn clean install -pl api -am
```

### Working with Python Agents
```bash
# Create custom agent
cp python-agent/agents/chat_helper python-agent/agents/my_custom -r
# Edit agent logic and register in main.py

# Test custom agent
TEST_MODE=true python3 main.py my-custom
```

### Docker Image Management
```bash
# Build specific images (sequential)
./ops-scripts/base/build-images.sh --sentrius --sentrius-keycloak

# Build specific images (concurrent - faster)
./ops-scripts/base/build-all-images-concurrent.sh --sentrius --sentrius-keycloak

# Build with development certificates
./ops-scripts/base/build-images.sh --all --include-dev-certs

# Build concurrently with development certificates (faster)
./ops-scripts/base/build-all-images-concurrent.sh --all --include-dev-certs
```

### Debugging Tips
- **Maven warnings**: Safe to ignore dependency duplication warnings
- **Database connection failures**: Check PostgreSQL is running and accessible
- **Keycloak authentication errors**: Verify Keycloak is running and client secrets match
- **Build timeouts**: Always use 60+ minute timeouts for builds, 30+ for tests
- **Python dependencies**: Use `pip3 install -r requirements.txt` before running agents

## Performance Expectations

| Operation | Time | Timeout Setting |
|-----------|------|-----------------|
| Maven build (clean install) | 7m24s | 60+ minutes |
| Maven test execution | 1m3s | 30+ minutes |
| Docker image build | 5-10m | 60+ minutes |
| Python dependency install | <1m | 10 minutes |
| Helm chart validation | <30s | 5 minutes |

**NEVER CANCEL** long-running operations. Builds may legitimately take 10+ minutes depending on network and system performance.

## Known Limitations

- **Database Required**: Cannot run API without PostgreSQL connection
- **Authentication Required**: Keycloak must be configured for full functionality  
- **Container Dependencies**: Full deployment requires Kubernetes cluster
- **Memory Requirements**: Recommended 8GB+ RAM for full local development
- **Network Dependencies**: Downloads Maven dependencies and container images

## Troubleshooting

### Build Failures
```bash
# Clear Maven cache if build issues occur
rm -rf ~/.m2/repository
mvn clean install

# Check Java version
java -version  # Should be 17+
mvn -version   # Should be 3.6+
```

### Runtime Issues
```bash
# Check required services
curl http://localhost:8180  # Keycloak health
curl http://localhost:5432  # PostgreSQL (may timeout, that's ok)

# Verify Python environment
cd python-agent && python3 --version && pip3 list
```

### Container Issues
```bash
# Reset Docker environment for local development
eval $(minikube docker-env)
docker images | grep sentrius
```

Always build and test your changes with the exact commands provided above to ensure compatibility with the CI/CD pipeline.