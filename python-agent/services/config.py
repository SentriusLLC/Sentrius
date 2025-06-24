"""
Configuration management for Sentrius Python Agent.
"""
import yaml
import os
import logging
from dataclasses import dataclass
from typing import Optional, Dict, Any

logger = logging.getLogger(__name__)


@dataclass
class KeycloakConfig:
    """Keycloak configuration."""
    server_url: str
    realm: str
    client_id: str
    client_secret: str


@dataclass
class AgentConfig:
    """Agent configuration."""
    name_prefix: str
    agent_type: str = "python"
    callback_url: str = "http://localhost:8080"
    api_url: str = "http://localhost:8080"
    heartbeat_interval: int = 30  # seconds


@dataclass
class LLMConfig:
    """LLM configuration."""
    enabled: bool = False
    provider: str = "openai"
    model: str = "gpt-3.5-turbo"
    api_key: Optional[str] = None
    endpoint: Optional[str] = None


@dataclass
class SentriusAgentConfig:
    """Main configuration class for Sentrius Python Agent."""
    keycloak: KeycloakConfig
    agent: AgentConfig
    llm: LLMConfig
    
    @classmethod
    def from_yaml(cls, config_path: str) -> 'SentriusAgentConfig':
        """Load configuration from YAML file."""
        try:
            with open(config_path, 'r') as f:
                config_data = yaml.safe_load(f)
            
            return cls(
                keycloak=KeycloakConfig(**config_data.get('keycloak', {})),
                agent=AgentConfig(**config_data.get('agent', {})),
                llm=LLMConfig(**config_data.get('llm', {}))
            )
        except Exception as e:
            logger.error(f"Failed to load configuration from {config_path}: {e}")
            raise
    
    @classmethod
    def from_env(cls) -> 'SentriusAgentConfig':
        """Load configuration from environment variables."""
        try:
            keycloak_config = KeycloakConfig(
                server_url=os.getenv('KEYCLOAK_SERVER_URL', 'http://localhost:8080'),
                realm=os.getenv('KEYCLOAK_REALM', 'sentrius'),
                client_id=os.getenv('KEYCLOAK_CLIENT_ID', ''),
                client_secret=os.getenv('KEYCLOAK_CLIENT_SECRET', '')
            )
            
            agent_config = AgentConfig(
                name_prefix=os.getenv('AGENT_NAME_PREFIX', 'python-agent'),
                agent_type=os.getenv('AGENT_TYPE', 'python'),
                callback_url=os.getenv('AGENT_CALLBACK_URL', 'http://localhost:8080'),
                api_url=os.getenv('AGENT_API_URL', 'http://localhost:8080'),
                heartbeat_interval=int(os.getenv('AGENT_HEARTBEAT_INTERVAL', '30'))
            )
            
            llm_config = LLMConfig(
                enabled=os.getenv('LLM_ENABLED', 'false').lower() == 'true',
                provider=os.getenv('LLM_PROVIDER', 'openai'),
                model=os.getenv('LLM_MODEL', 'gpt-3.5-turbo'),
                api_key=os.getenv('LLM_API_KEY'),
                endpoint=os.getenv('LLM_ENDPOINT')
            )
            
            return cls(
                keycloak=keycloak_config,
                agent=agent_config,
                llm=llm_config
            )
        except Exception as e:
            logger.error(f"Failed to load configuration from environment: {e}")
            raise
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert configuration to dictionary."""
        return {
            'keycloak': {
                'server_url': self.keycloak.server_url,
                'realm': self.keycloak.realm,
                'client_id': self.keycloak.client_id,
                'client_secret': self.keycloak.client_secret
            },
            'agent': {
                'name_prefix': self.agent.name_prefix,
                'agent_type': self.agent.agent_type,
                'callback_url': self.agent.callback_url,
                'api_url': self.agent.api_url,
                'heartbeat_interval': self.agent.heartbeat_interval
            },
            'llm': {
                'enabled': self.llm.enabled,
                'provider': self.llm.provider,
                'model': self.llm.model,
                'api_key': self.llm.api_key,
                'endpoint': self.llm.endpoint
            }
        }
    
    def save_to_yaml(self, config_path: str):
        """Save configuration to YAML file."""
        try:
            with open(config_path, 'w') as f:
                yaml.dump(self.to_dict(), f, default_flow_style=False)
        except Exception as e:
            logger.error(f"Failed to save configuration to {config_path}: {e}")
            raise