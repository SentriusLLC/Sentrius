# Keycloak Realm Configuration

This realm configuration file contains client definitions for Sentrius with dynamic secret injection.

## 🔐 Dynamic Secret Management

The Keycloak container now supports dynamic secret injection through environment variables:

- **SENTRIUS_API_CLIENT_SECRET** - Secret for sentrius-api client
- **SENTRIUS_LAUNCHER_CLIENT_SECRET** - Secret for sentrius-launcher-service client  
- **JAVA_AGENTS_CLIENT_SECRET** - Secret for java-agents client
- **MONITORING_AGENT_CLIENT_SECRET** - Secret for ai-agent-assessor client

## How It Works

1. **Template Processing**: The `sentrius-realm.json.template` file contains environment variable placeholders
2. **Runtime Substitution**: During container startup, the `process-realm-template.sh` script replaces placeholders with actual values
3. **Helm Integration**: The Helm chart generates OAuth2 secrets and passes them as environment variables
4. **Automatic Import**: Keycloak imports the processed realm with the dynamically generated secrets

## Environment Variable Integration

The Helm chart automatically:
- Generates random 32-character secrets when none are provided
- Passes these secrets as environment variables to the Keycloak container
- Ensures consistency between Helm-managed OAuth2 secrets and Keycloak realm configuration

## Fallback Behavior

If environment variables are not provided, the startup script generates default random secrets to ensure the container can start successfully.