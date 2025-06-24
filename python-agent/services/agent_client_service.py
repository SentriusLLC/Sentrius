"""
Agent client service for API communication with Sentrius server.
Equivalent to Java AgentClientService class.
"""
import json
import requests
import logging
import time
from typing import Dict, Any, Optional
from dataclasses import dataclass, asdict

logger = logging.getLogger(__name__)


@dataclass
class AgentRegistrationRequest:
    """Agent registration request data."""
    agent_name: str
    agent_callback_url: str


@dataclass
class AgentHeartbeat:
    """Agent heartbeat data."""
    status: str = "ACTIVE"
    last_activity: Optional[str] = None
    message: Optional[str] = None


@dataclass
class ProvenanceEvent:
    """Provenance event data."""
    event_type: str
    timestamp: str
    agent_id: str
    details: Dict[str, Any]


@dataclass
class TokenDTO:
    """Token data transfer object."""
    access_token: str
    token_type: str = "Bearer"


class AgentClientService:
    """Service for agent API communication with Sentrius server."""
    
    def __init__(self, api_base_url: str, keycloak_service):
        self.api_base_url = api_base_url.rstrip('/')
        self.keycloak_service = keycloak_service
        self.session = requests.Session()
        
    def _get_auth_headers(self) -> Dict[str, str]:
        """Get authorization headers with Keycloak token."""
        token = self.keycloak_service.get_keycloak_token()
        return {
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json'
        }
    
    def register_agent(self, agent_name: str, callback_url: str) -> Dict[str, Any]:
        """
        Register agent with the Sentrius API server.
        
        Args:
            agent_name: Name/ID of the agent
            callback_url: Callback URL for the agent
            
        Returns:
            Registration response data
        """
        url = f"{self.api_base_url}/api/v1/agent/register"
        headers = self._get_auth_headers()
        
        # Registration request doesn't need a body based on Java implementation
        try:
            response = self.session.post(url, headers=headers)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            logger.error(f"Agent registration failed: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise
    
    def send_heartbeat(self, agent_id: str, status: str = "ACTIVE", 
                      message: str = None) -> Dict[str, Any]:
        """
        Send heartbeat to the Sentrius API server.
        
        Args:
            agent_id: Agent identifier
            status: Agent status
            message: Optional status message
            
        Returns:
            Heartbeat response data
        """
        url = f"{self.api_base_url}/api/v1/agent/heartbeat"
        headers = self._get_auth_headers()
        
        heartbeat_data = AgentHeartbeat(
            status=status,
            last_activity=time.strftime('%Y-%m-%dT%H:%M:%S.%fZ'),
            message=message
        )
        
        try:
            response = self.session.post(
                url, 
                headers=headers, 
                json=asdict(heartbeat_data)
            )
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            logger.error(f"Heartbeat failed: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise
    
    def submit_provenance(self, event: ProvenanceEvent) -> Dict[str, Any]:
        """
        Submit provenance event to the Sentrius API server.
        
        Args:
            event: Provenance event data
            
        Returns:
            Submission response data
        """
        url = f"{self.api_base_url}/api/v1/agent/provenance/submit"
        headers = self._get_auth_headers()
        
        try:
            response = self.session.post(
                url,
                headers=headers,
                json=asdict(event)
            )
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            logger.error(f"Provenance submission failed: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise
    
    def create_session(self, username: str, ip_address: str) -> Dict[str, Any]:
        """
        Create a session log entry.
        
        Args:
            username: Username for the session
            ip_address: IP address of the client
            
        Returns:
            Session creation response data
        """
        # This appears to be a GET endpoint based on Java implementation
        url = f"{self.api_base_url}/api/v1/agent/session"
        headers = self._get_auth_headers()
        
        params = {
            'username': username,
            'ipAddress': ip_address
        }
        
        try:
            response = self.session.get(url, headers=headers, params=params)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            logger.error(f"Session creation failed: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise