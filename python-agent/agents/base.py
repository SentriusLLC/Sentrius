from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import logging

from services.sentrius_agent import SentriusAgent
from services.config import SentriusAgentConfig

logger = logging.getLogger(__name__)


class BaseAgent(ABC):
    """Abstract base class for all agents with Sentrius API integration."""
    
    def __init__(self, config_manager, name: Optional[str] = None):
        self.config_manager = config_manager
        self.name = name or self.__class__.__name__.lower().replace('agent', '')
        
        # Check if we're in test mode
        self.test_mode = config_manager.get_property('test.mode', 'false').lower() == 'true'
        
        if not self.test_mode:
            # Load configuration for Sentrius integration
            agent_config = config_manager.get_agent_config()
            keycloak_config = config_manager.get_keycloak_config()
            
            # Create SentriusAgentConfig from the loaded configuration
            from services.config import KeycloakConfig, AgentConfig, LLMConfig
            
            self.config = SentriusAgentConfig(
                keycloak=KeycloakConfig(
                    server_url=keycloak_config['server_url'],
                    realm=keycloak_config['realm'],
                    client_id=keycloak_config['client_id'],
                    client_secret=keycloak_config['client_secret']
                ),
                agent=AgentConfig(
                    name_prefix=agent_config['name_prefix'],
                    agent_type=agent_config['agent_type'],
                    callback_url=agent_config['callback_url'],
                    api_url=agent_config['api_url'],
                    heartbeat_interval=agent_config['heartbeat_interval']
                ),
                llm=LLMConfig()  # Default LLM config
            )
            
            # Initialize Sentrius agent
            self.sentrius_agent = SentriusAgent(self.config)
        else:
            logger.info("Running in test mode - external services disabled")
            self.sentrius_agent = None
        
        logger.info(f"Initialized {self.__class__.__name__}: {config_manager}")

    @abstractmethod
    def execute_task(self, task_data: Optional[Dict[str, Any]] = None):
        """Method to execute the agent's specific task."""
        pass
    
    def run(self, task_data: Optional[Dict[str, Any]] = None):
        """Main run method that handles agent lifecycle."""
        try:
            if self.sentrius_agent and not self.test_mode:
                with self.sentrius_agent:
                    logger.info(f"Starting {self.name} agent")
                    
                    # Submit start event
                    self.sentrius_agent.submit_provenance_event(
                        event_type="AGENT_START",
                        details={
                            "agent_name": self.name,
                            "agent_class": self.__class__.__name__
                        }
                    )
                    
                    # Execute the specific task
                    result = self.execute_task(task_data)
                    
                    # Submit completion event
                    self.sentrius_agent.submit_provenance_event(
                        event_type="AGENT_COMPLETE",
                        details={
                            "agent_name": self.name,
                            "status": "completed"
                        }
                    )
                    
                    logger.info(f"{self.name} agent completed successfully")
                    return result
            else:
                # Test mode - just execute the task
                logger.info(f"Starting {self.name} agent (test mode)")
                result = self.execute_task(task_data)
                logger.info(f"{self.name} agent completed successfully (test mode)")
                return result
                
        except Exception as e:
            logger.error(f"{self.name} agent failed: {e}")
            
            # Submit error event
            if self.sentrius_agent and not self.test_mode:
                try:
                    self.sentrius_agent.submit_provenance_event(
                        event_type="AGENT_ERROR",
                        details={
                            "agent_name": self.name,
                            "error": str(e),
                            "error_type": type(e).__name__
                        }
                    )
                except:
                    pass  # Don't fail if we can't submit error event
            
            raise
    
    def submit_provenance(self, event_type: str, details: Dict[str, Any]):
        """Submit a provenance event."""
        if self.sentrius_agent and not self.test_mode:
            self.sentrius_agent.submit_provenance_event(event_type, details)
        else:
            logger.info(f"Test mode - would submit provenance: {event_type} - {details}")
