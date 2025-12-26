# Sentrius

![Sentrius Dashboard](docs/images/dashboard.png)

**Sentrius** is a zero trust security platform for protecting your infrastructure. Monitor and control SSH connections, APIs, and RDP sessions with AI-powered agents, ensuring all access is secure and compliant with your organization's policies.

## 🚀 Quick Start

### Deploy with Kubernetes (Recommended)

```bash
# Build Docker images (3-7 minutes)
./ops-scripts/base/build-all-images-concurrent.sh --all --no-cache

# Deploy to local cluster
./ops-scripts/local/deploy-helm.sh

# Access services
kubectl port-forward -n dev service/sentrius-sentrius 8080:8080
```

Open http://localhost:8080 in your browser.

### Run Locally (Development)

```bash
# Build project
mvn clean install

# Start services (requires PostgreSQL and Keycloak)
./ops-scripts/local/run-sentrius.sh --build
```

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed deployment options.

## ✨ Key Features

### Zero Trust Security
Enforce zero trust policies with continuous authentication, authorization, and monitoring for every connection.

### SSH Session Management
![SSH Session Management](docs/images/ssh.png)

Secure SSH connections with real-time monitoring, command filtering, and session recording. Access through the web UI or terminal.

### AI-Powered Agent Designer
![Agent Designer](docs/images/agentdesigner.png)

Create custom agents using natural language prompts. Agents can monitor sessions, automate tasks, and provide user assistance.

### Enclaves & Access Control
Group hosts into logical enclaves with role-based access control for fine-grained permissions and simplified security oversight.

### Dynamic Rules Enforcement
Define flexible, context-aware rules that adapt to real-time changes (user risk score, time of day, IP ranges).

### Self-Healing System
Automatically detect, analyze, and repair system errors through intelligent coding agents. Configure patching policies per service with built-in security analysis.

### External Integrations
Integrate with GitHub, JIRA, and LLMs through secure zero-trust proxies. All integrations use access tokens with granular permissions.

## 📋 Prerequisites

**Required:**
- Java 17+
- Maven 3.6+
- PostgreSQL database
- Keycloak authentication server
- Docker & Kubernetes (for containerized deployment)

**Optional:**
- Neo4j (graph analysis)
- Kafka (event streaming)
- Python 3.12+ (Python agents)

## 📚 Documentation

- **[Deployment Guide](DEPLOYMENT.md)** - Deploy Sentrius locally, on Kubernetes, or cloud platforms
- **[Development Guide](DEVELOPMENT.md)** - Build, test, and contribute to Sentrius
- **[Custom Agents](CUSTOM_AGENTS.md)** - Create Java and Python agents for monitoring and automation
- **[Integrations](INTEGRATIONS.md)** - Connect with GitHub, JIRA, LLMs, and self-healing system
- **[API Documentation](docs/)** - REST API reference and guides

## 🏗️ Architecture

Sentrius consists of 12+ Maven modules organized for zero trust security:

```
sentrius/
├── core/               # Business logic, enclave management, policy enforcement
├── api/                # REST API and web interface
├── dataplane/          # Secure data transfer and processing
├── llm-core/           # Language model integration
├── integration-proxy/  # External service integrations (GitHub, JIRA, LLMs)
├── agent-launcher/     # Dynamic agent lifecycle management
├── provenance-core/    # Event tracking and audit framework
└── ...
```

See [DEVELOPMENT.md](DEVELOPMENT.md) for complete project structure.

## 🤝 Contributing

Contributions are welcome! To get started:

1. Fork the repository
2. Create a feature branch for your changes
3. Open a pull request with a clear description

See [DEVELOPMENT.md](DEVELOPMENT.md) for detailed development guidelines.

## 📄 License

Sentrius is licensed under the Apache License v2. See the [LICENSE](LICENSE) file for details.

## 📧 Contact

Questions or need commercial support?

**Email:** marc@sentrius.io

We're here to help you secure your infrastructure with Sentrius!
