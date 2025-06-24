"""
Main Sentrius Agent framework that integrates all services.
Equivalent to Java ChatAgent functionality.
"""
import time
import threading
import logging
import uuid
from typing import Optional, Dict, Any
from datetime import datetime, timezone

from .keycloak_service import KeycloakService
from .agent_client_service import AgentClientService, ProvenanceEvent
from .key_service import EphemeralKeyGen
from .config import SentriusAgentConfig

logger = logging.getLogger(__name__)


class SentriusAgent:
    """Main Sentrius Agent framework class."""
    
    def __init__(self, config: SentriusAgentConfig):
        self.config = config
        self.agent_id = f"{config.agent.name_prefix}-{uuid.uuid4().hex[:8]}"
        self.running = False
        self.heartbeat_thread: Optional[threading.Thread] = None
        
        # Initialize services
        self.keycloak_service = KeycloakService(
            server_url=config.keycloak.server_url,
            realm=config.keycloak.realm,
            client_id=config.keycloak.client_id,
            client_secret=config.keycloak.client_secret
        )
        
        self.agent_client_service = AgentClientService(
            api_base_url=config.agent.api_url,
            keycloak_service=self.keycloak_service
        )
        
        # Generate ephemeral keys for secure communication
        self.private_key, self.public_key = EphemeralKeyGen.generate_ephemeral_rsa_keypair()
        
        logger.info(f"Initialized Sentrius Agent: {self.agent_id}")
    
    def start(self):
        """Start the agent and begin registration process."""
        logger.info(f"Starting Sentrius Agent: {self.agent_id}")
        
        try:
            # Register with the API server
            self._register_agent()
            
            # Start heartbeat mechanism
            self._start_heartbeat()
            
            self.running = True
            logger.info(f"Sentrius Agent {self.agent_id} started successfully")
            
        except Exception as e:
            logger.error(f"Failed to start agent: {e}")
            self.stop()
            raise
    
    def stop(self):
        """Stop the agent and cleanup resources."""
        logger.info(f"Stopping Sentrius Agent: {self.agent_id}")
        
        self.running = False
        
        # Stop heartbeat thread
        if self.heartbeat_thread and self.heartbeat_thread.is_alive():
            self.heartbeat_thread.join(timeout=5)
        
        logger.info(f"Sentrius Agent {self.agent_id} stopped")
    
    def submit_provenance_event(self, event_type: str, details: Dict[str, Any]):
        """Submit a provenance event to the API server."""
        try:
            event = ProvenanceEvent(
                event_type=event_type,
                timestamp=datetime.now(timezone.utc).isoformat(),
                agent_id=self.agent_id,
                details=details
            )
            
            response = self.agent_client_service.submit_provenance(event)
            logger.debug(f"Provenance event submitted: {response}")
            
        except Exception as e:
            logger.error(f"Failed to submit provenance event: {e}")
            raise
    
    def get_agent_id(self) -> str:
        """Get the agent ID."""
        return self.agent_id
    
    def get_public_key_base64(self) -> str:
        """Get the base64 encoded public key."""
        return EphemeralKeyGen.get_base64_public_key(self.public_key)
    
    def decrypt_with_private_key(self, encrypted_data: str) -> str:
        """Decrypt data using the agent's private key."""
        return EphemeralKeyGen.decrypt_rsa_with_private_key(encrypted_data, self.private_key)
    
    def _register_agent(self):
        """Register the agent with the API server."""
        try:
            response = self.agent_client_service.register_agent(
                agent_name=self.agent_id,
                callback_url=self.config.agent.callback_url
            )
            logger.info(f"Agent registration successful: {response}")
            
            # Submit registration provenance event
            self.submit_provenance_event(
                event_type="AGENT_REGISTRATION",
                details={
                    "agent_id": self.agent_id,
                    "callback_url": self.config.agent.callback_url,
                    "agent_type": self.config.agent.agent_type
                }
            )
            
        except Exception as e:
            logger.error(f"Agent registration failed: {e}")
            raise
    
    def _start_heartbeat(self):
        """Start the heartbeat mechanism."""
        if self.heartbeat_thread and self.heartbeat_thread.is_alive():
            return
            
        self.heartbeat_thread = threading.Thread(target=self._heartbeat_worker, daemon=True)
        self.heartbeat_thread.start()
        logger.info("Heartbeat mechanism started")
    
    def _heartbeat_worker(self):
        """Heartbeat worker thread."""
        while self.running:
            try:
                response = self.agent_client_service.send_heartbeat(
                    agent_id=self.agent_id,
                    status="ACTIVE",
                    message="Agent running normally"
                )
                logger.debug(f"Heartbeat sent: {response}")
                
            except Exception as e:
                logger.error(f"Heartbeat failed: {e}")
            
            # Wait for next heartbeat interval
            time.sleep(self.config.agent.heartbeat_interval)
    
    def __enter__(self):
        """Context manager entry."""
        self.start()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit."""
        self.stop()