Sentrius

![image](docs/images/dashboard.png)

Sentrius is zero trust (and if you want AI assisted) management system. to protect your infrastructure. It is split 
into several maven projects. Agents can be leveraged to monitor and control infra ( SSH, APIs, RDP eventually), ensuring that all connections are secure and compliant with your organization's policies.
Agents can access external resources ( like LLMs or integrations ) via a zero trust assisted access token. 
sub-projects:

    core – Handles the core functionalities (e.g., SSH session management, zero trust policy enforcement).
    api – Provides a RESTful API layer to interface with the core module.
    dataplane – Offers dataplane functionality for secure data transfer and processing.
    integration-proxy – A proxy service that integrates with large language models (LLMs) and external services (like GitHub, JIRA) to enhance security and compliance. Supports dynamic MCP (Model Context Protocol) server management for GitHub integrations.
    llm-dataplane – A data processing layer that leverages LLMs for advanced analysis and decision-making in SSH sessions.
    ops-scripts – Contains operational scripts for deployment and management tasks.
    ai-agent – Java-based intelligent agent framework for monitoring and controlling SSH sessions.
    agent-launcher – Service for dynamically launching and managing agents.
    python-agent – Python-based agent framework for SSH session monitoring and user assistance.

Internally, Sentrius may still be referenced by its former name, SSO (SecureShellOps), in certain scripts or configurations.
Table of Contents

    Key Features
    Project Structure
    Prerequisites
    Installation
    Configuration
    Running Sentrius
    Helm Chart Deployment
    Testing
    Integrations
    Custom Agents
    Usage
    API Documentation
    Contributing
    License
    Contact

Key Features

    Zero Trust Security
    Sentrius enforces zero trust policies, ensuring that every SSH connection is authenticated, authorized, and constantly monitored.

    Enclaves
    Group hosts into logical enclaves and apply role-based access control for fine-grained permissions. Simplify security oversight by separating and organizing your infrastructure.

    Dynamic Rules Enforcement
    Define flexible, context-aware rules that adapt to real-time changes in your environment (e.g., user risk score, time of day, IP ranges).

    REST API
    Manage your SSH configurations, enclaves, security rules, and sessions programmatically using a well-documented REST API.

    Self-Healing System
    Automatically detects, analyzes, and repairs system errors through intelligent coding agents. Configure patching policies (immediate, off-hours, or never) per pod/service, with built-in security analysis to prevent healing of security-sensitive errors without manual review. When configured, the system can automatically create GitHub pull requests with fixes.

Custom SSH Server responds via Sentrius UI or terminals
![image](docs/images/ssh.png)

Agent Designer supports natural language prompts to create custom agents that can monitor and control SSH sessions, automate tasks, and provide user assistance. The Agent Designer allows you to define agent behavior, capabilities, and interactions with the Sentrius platform.
![image](docs/images/agentdesigner.png)

Project Structure

Sentrius consists of multiple sub-projects:

    core
    Contains business logic, including:
        Enclave management
        Zero trust policy enforcement
        Secure SSH connection handling

    api
    A RESTful interface for interacting with the core functionalities. The api module exposes endpoints that let you:
        Create and manage enclaves
        Configure security rules
        Visualize SSH sessions and logs
        Handle user access and authentication

sentrius/
├── core/
│   ├── src/
│   └── pom.xml
├── api/
│   ├── src/
│   └── pom.xml
├── ops-scripts/
│   └── gcp/
│       └── deploy-helm.sh
├── pom.xml
└── ...

Prerequisites

    Java 17 or later
    Apache Maven 3.6+
    Database (PostgreSQL, MySQL, etc.) for storing session and configuration data
    Keycloak for user authentication and authorization
    (Optional) Docker & Kubernetes if you plan to deploy on a containerized environment
    (Optional) python 3.6+ for the python agent

Installation

    Clone the Repository

git clone https://github.com/your-organization/sentrius.git
cd sentrius

#Running Sentrius

Build the projects from root ( mvn clean install ) to ensure all dependencies are resolved and the modules are compiled.

For convenience the ops/local directory contains a "run-sentrius.sh" script which will start the core and api 
modules. You can run this script from the project root.
This assumes you have a database available, keycloak running, and the necessary configurations. We now require an 
OTEL endpoint, along with neo4j and kafka (but these are optional).:

    ./ops/local/run-sentrius.sh

It is simpler to run a kubernetes deployment, which is described in the Deployment. To do this, build as you would 
above.

Build the images in your local Docker registry (note this builds all images, including core, api, and any other modules):

    /build-images.sh --all --no-cache

Run the Helm deployment script to deploy Sentrius to your local Kubernetes cluster:

    ./ops-scripts/local/deploy-helm.sh


## If Not using TLS
You may wish to forward ports so you can access the services locally. The following commands will forward the necessary ports for the core and api modules:
    kubectl port-forward -n dev service/sentrius-sentrius 8080:8080
    kubectl port-forward -n dev service/sentrius-keycloak 8081:8081

This will require that you either change the hostnames in the deploy-helm script or add entries to your /etc/hosts file to point to localhost for the services.
    127.0.0.1 sentrius-sentrius
    127.0.0.1 sentrius-keycloak

## If Using TLS
The deploy script will automatically install cert-manager and create self-signed certificates for the services. You can access the services via:

    https://sentrius-dev.local
    https://keycloak-dev.local

Add these to /etc/hosts file pointing to your minikube or local cluster IP.
    

There is a GCP deployment that is hasn't been tested in some time. You can find it in the ops-scripts/gcp directory.

You will need to ensure you link to your GKE cluster and have the necessary permissions to deploy resources.

    ./ops-scripts/gcp/deploy-helm.sh <helm-release-name> <gcp-project-id> <any-other-key-or-params>

You are welcome to run the core and api modules separately, as needed. You can start the core module by running:

    mvn install
    cd api
    mvn spring-boot:run

## Testing

### CI/CD Testing

Sentrius includes comprehensive CI/CD testing for Helm charts and Java builds:

- **Automated testing** runs on every push and pull request via GitHub Actions
- **Helm chart validation** including linting, template rendering, and schema validation
- **Integration testing** with Kubernetes clusters for deployment validation

### Local Testing

Test Helm charts locally before deployment:

    # Test all charts
    ./ops-scripts/test-helm-charts.sh
    
    # Test specific aspects
    ./ops-scripts/test-helm-charts.sh lint      # Lint charts
    ./ops-scripts/test-helm-charts.sh template  # Test rendering
    ./ops-scripts/test-helm-charts.sh config    # Test configurations

For detailed testing documentation, see [docs/helm-testing.md](docs/helm-testing.md).

Build the Project

Sentrius uses Maven for its build process. Ensure Maven is installed and then run:

    mvn clean install

    This command will build both the core and api sub-projects, downloading any required dependencies.

Configuration

Sentrius requires properties in order to connect to databases, authenticate users, and configure SSH session parameters. You can supply them in src/main/resources/application.properties or via external configuration (e.g., environment variables or config files).

Typical settings include:

    Database Configuration

spring.datasource.url=jdbc:postgresql://localhost:5432/sentrius
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update

Security & Authentication

# JWT or OAuth
To configure Keycloak, you can use the following properties:

    keycloak.realm=sentrius
    keycloak.base-url=${KEYCLOAK_BASE_URL:http://localhost:8180}
    spring.security.oauth2.client.registration.keycloak.client-secret=${KEYCLOAK_SECRET:defaultSecret}
    
    spring.security.oauth2.client.registration.keycloak.client-id=sentrius-api
    spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code
    spring.security.oauth2.client.registration.keycloak.redirect-uri=${BASE_URL:http://localhost:8080}/login/oauth2/code/keycloak
    spring.security.oauth2.client.registration.keycloak.scope=openid,profile,email
    
    spring.security.oauth2.resourceserver.jwt.issuer-uri=${KEYCLOAK_BASE_URL:http://localhost:8180}/realms/sentrius
    spring.security.oauth2.client.provider.keycloak.issuer-uri=${KEYCLOAK_BASE_URL:http://localhost:8180}/realms/sentrius


SSH Settings

    sentrius.ssh.port=22
    sentrius.ssh.connection-timeout=30000

    Core and API Specifics
        Core might need additional application-specific properties (e.g., caching, logging).
        The API often needs separate configurations for its own port, API versioning, or logging settings.

Feel free to structure your configs based on your environment (dev/test/prod). For large-scale deployments, we recommend using a secure secrets manager (HashiCorp Vault, AWS Secrets Manager, etc.) to avoid storing sensitive information in plain text.

## Helm Chart Deployment

Sentrius provides comprehensive Helm charts for Kubernetes deployment across multiple environments. There are two main charts available:

### Available Charts

1. **sentrius-chart** - Complete Sentrius deployment with all services
2. **sentrius-chart-launcher** - Lightweight chart focused on the launcher service

### Quick Start

#### Local Deployment

```bash
# Build all images
./build-images.sh --all --no-cache

# Deploy to local Kubernetes cluster (HTTP)
./ops-scripts/local/deploy-helm.sh

# OR deploy with TLS enabled for secure transport
./ops-scripts/local/deploy-helm.sh --tls

# OR deploy with TLS and auto-install cert-manager
./ops-scripts/local/deploy-helm.sh --tls --install-cert-manager

# Forward ports for local access (HTTP deployment)
kubectl port-forward -n dev service/sentrius-sentrius 8080:8080
kubectl port-forward -n dev service/sentrius-keycloak 8081:8081
```

**For HTTP deployment**, add to `/etc/hosts`:
```
127.0.0.1 sentrius-sentrius
127.0.0.1 sentrius-keycloak
```

**For TLS deployment**, add to `/etc/hosts`:
```
127.0.0.1 sentrius-dev.local
127.0.0.1 keycloak-dev.local
```

**TLS Requirements:**
- cert-manager must be installed in your cluster. You can:
  - Install manually: `kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml`
  - Use auto-install flag: `./ops-scripts/local/deploy-helm.sh --tls --install-cert-manager`
- Access via: `https://sentrius-dev.local` and `https://keycloak-dev.local`
- Self-signed certificates will be automatically generated

#### GCP/GKE Deployment

```bash
# Deploy to GKE cluster
./ops-scripts/gcp/deploy-helm.sh <helm-release-name> <gcp-project-id> <any-other-params>
```

### Chart Configuration

#### Key Configuration Options

**Environment Settings:**
- `environment`: Set to "local", "gke", "aws", or "azure"
- `tenant`: Your tenant identifier
- `subdomain`: Domain for your deployment

**Core Services:**
- `sentrius.image.repository`: Core Sentrius image repository
- `llmproxy.image.repository`: LLM proxy image repository
- `postgres.storageSize`: Database storage allocation

**Ingress Configuration:**
```yaml
ingress:
  enabled: true
  class: "nginx"  # or "gce" for GKE
  tlsEnabled: true
  annotations:
    gke:
      kubernetes.io/ingress.class: gce
      networking.gke.io/managed-certificates: wildcard-cert
```

**TLS/SSL Configuration:**
```yaml
certificates:
  enabled: true  # Enable certificate generation
  issuer: "letsencrypt-prod"  # For AWS/Azure (cert-manager)

# For local development with self-signed certificates:
environment: local
certificates:
  enabled: true
ingress:
  tlsEnabled: true
```

**Agent Configuration:**
```yaml
sentriusagent:
  image:
    repository: sentrius-agent
  oauth2:
    client_id: java-agents
    client_secret: your-secret

sentriusaiagent:
  image:
    repository: sentrius-ai-agent
  oauth2:
    client_id: java-agents
```

#### Custom Values Example

Create a `my-values.yaml` file:
```yaml
environment: "gke"
tenant: "my-company"
subdomain: "my-company.sentrius.cloud"

sentrius:
  image:
    repository: "my-registry/sentrius"
    tag: "v1.0.0"

postgres:
  storageSize: "20Gi"

ingress:
  enabled: true
  tlsEnabled: true
  class: "gce"
```

Deploy with custom values:
```bash
helm install my-sentrius sentrius-chart -f my-values.yaml
```

### Multi-Environment Support

The charts support multiple deployment environments with different configurations:

**Local Development:**
- Uses NodePort services
- Minimal resource requirements
- In-memory storage options

**GKE (Google Cloud):**
- Uses LoadBalancer services
- Managed certificates
- Persistent storage

**AWS:**
- ALB ingress support
- EBS storage classes
- AWS-specific annotations

**Azure:**
- Azure Load Balancer integration
- Azure disk storage
- Azure-specific networking

### Helm Testing

For comprehensive testing documentation including CI/CD testing, local testing, and troubleshooting, see [docs/helm-testing.md](docs/helm-testing.md).

## Integrations

Sentrius supports external service integrations through the integration-proxy module, providing secure, zero-trust access to external APIs and services.

### GitHub Integration

The GitHub MCP (Model Context Protocol) integration enables secure access to GitHub repositories, issues, and pull requests through dynamically launched MCP server containers.

**Features:**
- Query GitHub issues and pull requests
- Access repository information
- Clone and interact with repositories
- All operations use zero-trust security model

**Setup:**

1. **Store GitHub Token:**
   Create an `IntegrationSecurityToken` with:
   - `connectionType`: "github"
   - `connectionInfo`: Your GitHub Personal Access Token

2. **Launch MCP Server:**
   ```bash
   curl -X POST "http://integration-proxy:8080/api/v1/github/mcp/launch?tokenId=<TOKEN_ID>" \
     -H "Authorization: Bearer <JWT_TOKEN>"
   ```

3. **Access via Service URL:**
   The response includes a `serviceUrl` for accessing the GitHub MCP server within the cluster.

For detailed documentation, see [integration-proxy/GITHUB_INTEGRATION.md](integration-proxy/GITHUB_INTEGRATION.md).

### JIRA Integration

The JIRA integration provides secure proxy access to JIRA APIs for ticket management and tracking.

**Available Endpoints:**
- `/api/v1/jira/rest/api/3/search` - Search for JIRA issues
- `/api/v1/jira/rest/api/3/issue` - Get issue details
- `/api/v1/jira/rest/api/3/issue/comment` - Manage issue comments
- `/api/v1/jira/rest/api/3/issue/assignee` - Assign issues

All JIRA requests are authenticated through Keycloak and validated against the user's permissions.

## Self-Healing System

Sentrius includes an intelligent self-healing system that automatically detects, analyzes, and repairs errors in your infrastructure.

### Key Features

- **Automatic Error Detection**: Continuously monitors the error output table and OpenTelemetry data for system errors
- **Security Analysis**: Automatically analyzes errors to determine if they pose security concerns before attempting repairs
- **Flexible Patching Policies**: Configure per-pod/service policies for when repairs should be applied:
  - **Immediate**: Apply fixes as soon as errors are detected
  - **Off-Hours**: Queue fixes to apply during configured maintenance windows (default: 10 PM - 6 AM)
  - **Never**: Disable self-healing for critical services that require manual intervention
- **Coding Agent Deployment**: Automatically launches isolated coding agent pods to analyze errors and generate fixes
- **Docker Image Building**: Spins up Kubernetes Jobs using Kaniko to build and push Docker images with the fixes
- **Complete Workflow Automation**: Coordinates agent launch, monitoring, image building, and optional GitHub PR creation
- **Read-Only Agent Monitoring**: View real-time agent activity and healing progress through the UI (non-security errors only)
- **GitHub Integration**: Optionally create pull requests with fixes when GitHub credentials are configured

### Configuration

Self-healing can be configured through the web UI or via API:

#### Web UI Configuration

1. Navigate to **Self-Healing Configuration** (`/sso/v1/self-healing/config`)
2. Click **Add Pod Configuration** to create a new policy
3. Set the pod name, type, and patching policy using the slider control
4. Enable or disable self-healing for the pod

#### API Configuration

```bash
# Create or update a self-healing configuration
curl -X POST http://localhost:8080/api/v1/self-healing/config \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{
    "podName": "sentrius-api",
    "podType": "api",
    "patchingPolicy": "OFF_HOURS",
    "enabled": true
  }'

# Get all configurations
curl http://localhost:8080/api/v1/self-healing/config \
  -H "Authorization: Bearer <TOKEN>"
```

#### Application Properties

Self-healing configuration is managed through Helm values and automatically populated into the ConfigMap. Update `values.yaml`:

```yaml
selfHealing:
  enabled: true
  offHours:
    start: 22  # 10 PM
    end: 6     # 6 AM
  codingAgent:
    clientId: "coding-agents"
    clientSecret: ""  # Set in secrets
  agentLauncher:
    url: "http://sentrius-agents-launcherservice:8080"
  builder:
    namespace: "dev"
    image: "gcr.io/kaniko-project/executor:latest"
    timeoutSeconds: 1800
    autoBuild: true
  docker:
    registry: ""  # Leave empty for local registry
  github:
    enabled: false  # Auto-enabled if GitHub integration exists
    apiUrl: "https://api.github.com"
    owner: ""
    repo: ""
```

**Important**: Self-healing requires GitHub integration to be configured in the integration tokens table. The system will automatically detect if a GitHub token exists and only proceed if configured. To add a GitHub integration token, navigate to the Integration Settings in the UI and add a token with `connectionType: "github"`.

### Viewing Healing Sessions

Monitor active and completed healing sessions:

1. Navigate to **Self-Healing Sessions** (`/sso/v1/self-healing/sessions`)
2. Filter by status: All, Active, or Completed
3. View detailed information about each session including:
   - Agent activity and logs
   - Security analysis results
   - Docker build status
   - GitHub PR links (if created)
   - Error details and resolution

### How It Works

The self-healing workflow consists of several automated steps:

1. **Error Detection**: The system scans the error_output table every 5 minutes for new errors
2. **Policy Check**: Determines if healing is enabled for the affected pod and checks the patching policy
3. **Security Analysis**: Analyzes error logs for security-related keywords
4. **Agent Launch**: If not a security concern, launches a coding agent pod to analyze and fix the error
5. **Code Repair**: The coding agent examines the error, generates fixes, and commits changes
6. **Docker Build**: A Kubernetes Job is created to build a new Docker image with the fixes using Kaniko
7. **GitHub PR**: If configured, creates a pull request with the changes
8. **Completion**: Updates the healing session with results and status

The entire workflow is asynchronous and can handle multiple concurrent healing sessions.

### Security Considerations

The self-healing system includes built-in safety mechanisms:

- **GitHub Integration Required**: Self-healing only proceeds if a GitHub integration token is configured in the system. This ensures all fixes can be tracked via pull requests.
- **Security Analysis**: Errors containing security-related keywords (authentication, authorization, vulnerability, etc.) are flagged and require manual review before healing proceeds
- **No Visibility Restriction**: Security-flagged errors are hidden from general users until cleared by administrators
- **Audit Trail**: All healing attempts are logged and tracked in the `self_healing_session` table
- **Isolated Execution**: Healing agents run in isolated Kubernetes pods with limited permissions

### Manual Triggering

You can manually trigger self-healing for specific errors (requires GitHub integration to be configured):

1. Navigate to **Error Logs** (`/sso/v1/notifications/error/log/get`)
2. Click **Trigger Self-Healing** on any error
3. Monitor progress in the Self-Healing Sessions view

Or via API:

```bash
curl -X POST http://localhost:8080/api/v1/self-healing/trigger/{errorId} \
  -H "Authorization: Bearer <TOKEN>"
```

**Note**: If GitHub integration is not configured, the trigger will fail with a message prompting you to add a GitHub integration token first.

### Database Schema

The self-healing system uses three main tables:

- `self_healing_config`: Stores patching policies per pod/service
- `self_healing_session`: Tracks each healing attempt and its status
- `error_output`: Extended with healing status and security analysis fields

## Custom Agents

Sentrius supports both Java and Python-based custom agents that can extend the platform's functionality for monitoring, automation, and user assistance.

### Java Agents

Java agents are built using the Spring Boot framework and integrate with the Sentrius ecosystem through the agent launcher service.

#### Creating a Custom Java Agent

1. **Create a new Spring Boot module** following the pattern of existing agents:
   ```
   my-custom-agent/
   ├── src/main/java/
   │   └── io/sentrius/agent/mycustom/
   │       ├── MyCustomAgent.java
   │       └── MyCustomAgentConfig.java
   └── pom.xml
   ```

2. **Implement the Agent Interface:**
   ```java
   @Component
   @ConditionalOnProperty(name = "agents.mycustom.enabled", havingValue = "true")
   public class MyCustomAgent implements ApplicationListener<ApplicationReadyEvent> {
       
       @Autowired
       private AgentService agentService;
       
       @Override
       public void onApplicationEvent(ApplicationReadyEvent event) {
           // Register agent and start processing
           agentService.register(this);
       }
   }
   ```

3. **Configuration Properties:**
   ```java
   @ConfigurationProperties(prefix = "agents.mycustom")
   @Data
   public class MyCustomAgentConfig {
       private boolean enabled = false;
       private String name = "my-custom-agent";
       private String description = "Custom agent for specialized tasks";
   }
   ```

4. **Add to application.properties:**
   ```properties
   agents.mycustom.enabled=true
   agents.mycustom.name=my-custom-agent
   agents.mycustom.description=Custom agent for specialized tasks
   ```

5. **Deploy with Helm Chart:**
   ```yaml
   # Add to values.yaml
   mycustomagent:
     image:
       repository: my-custom-agent
       tag: latest
     oauth2:
       client_id: java-agents
       client_secret: your-secret
   ```

#### Java Agent Features

- **Zero Trust Integration**: Automatic ZTAT (Zero Trust Access Token) handling
- **Provenance Tracking**: Built-in event logging and audit trails
- **LLM Integration**: Access to language models through the LLM proxy
- **Session Monitoring**: Real-time SSH session monitoring capabilities
- **RESTful APIs**: Full access to Sentrius APIs and data

### Python Agents

Python agents provide a flexible framework for creating custom automation and user assistance tools.

#### Creating a Custom Python Agent

1. **Set up the agent structure:**
   ```python
   # agents/my_custom/my_custom_agent.py
   from agents.base import BaseAgent

   class MyCustomAgent(BaseAgent):
       def __init__(self, config_manager):
           super().__init__(config_manager, name="my-custom-agent")
           self.agent_definition = config_manager.get_agent_definition('my.custom')
       
       def execute_task(self, task_data=None):
           # Your custom logic here
           self.submit_provenance(
               event_type="CUSTOM_TASK",
               details={"task": "custom_operation", "data": task_data}
           )
           
           return {
               "status": "completed",
               "result": "Custom task executed successfully"
           }
   ```

2. **Create agent configuration:**
   ```yaml
   # my-custom.yaml
   description: "Custom agent that performs specialized tasks"
   context: |
     You are a custom agent designed to handle specific business logic.
     Process requests according to your specialized capabilities.
   ```

3. **Add to application.properties:**
   ```properties
   agent.my.custom.config=my-custom.yaml
   agent.my.custom.enabled=true
   ```

4. **Register in main.py:**
   ```python
   from agents.my_custom.my_custom_agent import MyCustomAgent

   AVAILABLE_AGENTS = {
       'chat-helper': ChatHelperAgent,
       'my-custom': MyCustomAgent,  # Add your agent here
   }
   ```

5. **Run your custom agent:**
   ```bash
   python main.py my-custom --task-data '{"operation": "process_data"}'
   ```

#### Python Agent Features

- **API Integration**: Full access to Sentrius APIs using JWT authentication
- **Configuration Management**: Support for properties files and YAML configurations
- **LLM Proxy Access**: Integration with language models for AI-powered tasks
- **Provenance Submission**: Automatic event tracking and audit logging
- **Keycloak Authentication**: Built-in OAuth2/JWT token management

#### Running Python Agents

```bash
# With properties configuration
python main.py my-custom --config my-app.properties

# With environment variables
export KEYCLOAK_BASE_URL=http://localhost:8180
export KEYCLOAK_CLIENT_ID=python-agents
python main.py my-custom

# Test mode (no external services)
TEST_MODE=true python main.py my-custom
```

### Agent Development Best Practices

1. **Authentication**: Always use proper OAuth2/JWT authentication
2. **Provenance**: Submit detailed provenance events for audit trails
3. **Error Handling**: Implement robust error handling and logging
4. **Configuration**: Use environment-specific configurations
5. **Testing**: Test agents in isolation before integration
6. **Documentation**: Document agent capabilities and configuration options

For detailed Python agent documentation, see [python-agent/README.md](python-agent/README.md).

Contributing

Contributions of all forms are welcome! To get started:

    Fork the repository.
    Create a feature branch for your changes.
    Open a pull request back into the main branch, describing your changes and rationale.

If you encounter any issues or have requests, feel free to open a GitHub Issue. We actively review and address bug reports, feature requests, and general improvements.
License

Sentrius is licensed under the MIT License. For more details, please see the LICENSE file.
Contact

Questions, feedback, or need commercial support? Reach out to the project maintainers:

Email: marc@sentrius.io

We’re always happy to help you secure your infrastructure with Sentrius!
