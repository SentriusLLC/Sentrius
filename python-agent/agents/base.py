from abc import ABC, abstractmethod
from typing import Dict, Any, Optional
import logging

from services.sentrius_agent import SentriusAgent
from services.config import SentriusAgentConfig

logger = logging.getLogger(__name__)


class BaseAgent(ABC):
    """Abstract base class for all agents with Sentrius API integration."""
    
    def __init__(self, name: str, config_path: Optional[str] = None, config: Optional[SentriusAgentConfig] = None):
        self.name = name
        
        # Load configuration
        if config:
            self.config = config
        elif config_path:
            self.config = SentriusAgentConfig.from_yaml(config_path)
        else:
            self.config = SentriusAgentConfig.from_env()
        
        # Initialize Sentrius agent
        self.sentrius_agent = SentriusAgent(self.config)
        
        logger.info(f"Initialized {self.__class__.__name__}: {self.name}")

    @abstractmethod
    def execute_task(self):
        """Method to execute the agent's specific task."""
        pass
    
    def run(self):
        """Main run method that handles agent lifecycle."""
        try:
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
                self.execute_task()
                
                # Submit completion event
                self.sentrius_agent.submit_provenance_event(
                    event_type="AGENT_COMPLETE",
                    details={
                        "agent_name": self.name,
                        "status": "completed"
                    }
                )
                
                logger.info(f"{self.name} agent completed successfully")
                
        except Exception as e:
            logger.error(f"{self.name} agent failed: {e}")
            
            # Submit error event
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
        self.sentrius_agent.submit_provenance_event(event_type, details)
