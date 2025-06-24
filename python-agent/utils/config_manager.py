"""
Configuration management for the Sentrius Python Agent.
Supports loading from properties files and YAML files, similar to Java Spring configuration.
"""
import os
import yaml
import logging
from typing import Dict, Any, Optional, List
from pathlib import Path

logger = logging.getLogger(__name__)


class ConfigManager:
    """Manages configuration loading from properties and YAML files."""
    
    def __init__(self, properties_file: str = "application.properties"):
        self.properties_file = properties_file
        self.properties = {}
        self.agent_configs = {}
        self._load_properties()
        self._load_agent_configs()
    
    def _load_properties(self):
        """Load configuration from properties file with environment variable substitution."""
        properties_path = Path(self.properties_file)
        if not properties_path.exists():
            logger.warning(f"Properties file {self.properties_file} not found")
            return
        
        try:
            with open(properties_path, 'r') as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#'):
                        if '=' in line:
                            key, value = line.split('=', 1)
                            # Handle environment variable substitution
                            value = self._substitute_env_vars(value)
                            self.properties[key.strip()] = value.strip()
            
            logger.info(f"Loaded {len(self.properties)} properties from {self.properties_file}")
        except Exception as e:
            logger.error(f"Error loading properties file: {e}")
    
    def _substitute_env_vars(self, value: str) -> str:
        """Substitute environment variables in property values."""
        # Handle ${VARIABLE:default} pattern
        import re
        pattern = r'\$\{([^:}]+):([^}]*)\}'
        
        def replacer(match):
            env_var = match.group(1)
            default_val = match.group(2)
            return os.getenv(env_var, default_val)
        
        return re.sub(pattern, replacer, value)
    
    def _load_agent_configs(self):
        """Load agent-specific configuration files referenced in properties."""
        agent_config_keys = [k for k in self.properties.keys() if k.endswith('.config')]
        
        for config_key in agent_config_keys:
            yaml_file = self.properties[config_key]
            agent_name = config_key.replace('.config', '').replace('agent.', '')
            
            try:
                yaml_path = Path(yaml_file)
                if yaml_path.exists():
                    with open(yaml_path, 'r') as f:
                        config = yaml.safe_load(f)
                        self.agent_configs[agent_name] = config
                        logger.info(f"Loaded agent config for {agent_name} from {yaml_file}")
                else:
                    logger.warning(f"Agent config file {yaml_file} not found for {agent_name}")
            except Exception as e:
                logger.error(f"Error loading agent config {yaml_file}: {e}")
    
    def get_property(self, key: str, default: Any = None) -> Any:
        """Get a property value with optional default."""
        return self.properties.get(key, default)
    
    def get_keycloak_config(self) -> Dict[str, str]:
        """Get Keycloak configuration."""
        return {
            'server_url': self.get_property('keycloak.base-url', 'http://localhost:8180'),
            'realm': self.get_property('keycloak.realm', 'sentrius'),
            'client_id': self.get_property('keycloak.client-id', 'python-agents'),
            'client_secret': self.get_property('keycloak.client-secret')
        }
    
    def get_agent_config(self) -> Dict[str, Any]:
        """Get agent configuration."""
        return {
            'name_prefix': self.get_property('agent.name.prefix', 'python-agent'),
            'agent_type': self.get_property('agent.type', 'python'),
            'callback_url': self.get_property('agent.callback.url', 'http://localhost:8093'),
            'api_url': self.get_property('agent.api.url', 'http://localhost:8080/'),
            'heartbeat_interval': int(self.get_property('agent.heartbeat.interval', '30'))
        }
    
    def get_llm_config(self) -> Dict[str, Any]:
        """Get LLM configuration."""
        return {
            'endpoint': self.get_property('agent.llm.endpoint', 'http://localhost:8084/'),
            'enabled': self.get_property('agent.llm.enabled', 'true').lower() == 'true'
        }
    
    def get_agent_definition(self, agent_name: str) -> Optional[Dict[str, Any]]:
        """Get agent definition from loaded YAML configs."""
        return self.agent_configs.get(agent_name)
    
    def get_enabled_agents(self) -> List[str]:
        """Get list of enabled agents."""
        enabled_agents = []
        for key, value in self.properties.items():
            if key.endswith('.enabled') and value.lower() == 'true':
                agent_name = key.replace('.enabled', '').replace('agent.', '')
                enabled_agents.append(agent_name)
        return enabled_agents
    
    def is_agent_enabled(self, agent_name: str) -> bool:
        """Check if a specific agent is enabled."""
        enabled_key = f'agent.{agent_name}.enabled'
        return self.get_property(enabled_key, 'false').lower() == 'true'