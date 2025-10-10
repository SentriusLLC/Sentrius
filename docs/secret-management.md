# Sentrius Secret Management

## Overview

Hardcoded secrets have been removed from the Helm charts and application properties files. The system now supports both dynamic secret generation and external secret management.

## Dynamic Secret Generation

When no secrets are provided in values.yaml, the Helm charts will automatically generate random secrets for:

- OAuth2 client secrets (32 characters)
- Database passwords (32 characters) 
- Keystore passwords (24 characters)
- Keycloak admin passwords (24 characters)
- Neo4j authentication strings (16 character passwords)

## Providing Custom Secrets

You can override the generated secrets by setting them in your values.yaml:

```yaml
# Example custom secrets
secrets:
  db:
    username: "my-db-user"
    password: "my-secure-password"
    keystorePassword: "my-keystore-password"

sentrius:
  oauth2:
    client_secret: "my-oauth2-secret"

keycloak:
  adminPassword: "my-keycloak-admin-password"
  clientSecret: "my-keycloak-client-secret"
  db:
    password: "my-keycloak-db-password"

neo4j:
  env:
    NEO4J_AUTH: "neo4j/my-neo4j-password"
```

## Environment Variables

Application properties files now use environment variables with fallback defaults:

- `KEYCLOAK_CLIENT_SECRET` - OAuth2 client secret for Keycloak
- `DATABASE_PASSWORD` - Database password (defaults to "password")
- `KEYSTORE_PASSWORD` - Keystore password (defaults to "keystorepassword")

## Keycloak Realm Dynamic Configuration

The Keycloak Docker container now supports dynamic realm configuration with automatic secret injection:

### How It Works

1. **Template Processing**: The Keycloak realm configuration uses a template file (`sentrius-realm.json.template`) with environment variable placeholders
2. **Runtime Substitution**: During container startup, secrets are injected via environment variables:
   - `SENTRIUS_API_CLIENT_SECRET` - Secret for sentrius-api client
   - `SENTRIUS_LAUNCHER_CLIENT_SECRET` - Secret for sentrius-launcher-service client
   - `JAVA_AGENTS_CLIENT_SECRET` - Secret for java-agents client
   - `AI_AGENT_ASSESSOR_CLIENT_SECRET` - Secret for ai-agent-assessor client
   - `SENTRIUS_RDPPROXY_CLIENT_SECRET` - Secret for rdp proxy client
3. **Helm Integration**: The Helm chart automatically generates these secrets and passes them to the Keycloak container
4. **Fallback Generation**: If no secrets are provided, the container generates secure random defaults

### Build Integration

When building the Keycloak container with `./build-images.sh --sentrius-keycloak`, the system:
- Includes the realm template and processing script
- Configures automatic secret substitution during startup
- Ensures consistency between Helm-generated OAuth2 secrets and Keycloak realm configuration

## Production Deployment

For production environments, it is recommended to:

1. Use an external secret management system (HashiCorp Vault, AWS Secrets Manager, etc.)
2. Set all secrets explicitly in your values.yaml file
3. Use Kubernetes secrets or external secret operators
4. Never commit secrets to version control

## Removed Hardcoded Secrets

The following hardcoded secrets were removed:

- `nGkEukexSWTvDzYjSkDmeUlM0FJ5Jhh0` (multiple OAuth2 client secrets)
- `e4WgJovH8MzcAvRnFg3rROAbeDIwiYmx` (agent client secret)
- `KLJMLKSDJGlkj23@#jasdlkjg@#dsagsagdsag` (AI agent client secret)
- `neo4j/testingsecret` (Neo4j authentication)
- Base64 encoded database credentials
- Hardcoded keystore passwords