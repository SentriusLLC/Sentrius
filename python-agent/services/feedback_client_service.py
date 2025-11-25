"""
Feedback client service for submitting and retrieving agent feedback.
Integrates with the RLHF (Reinforcement Learning from Human Feedback) system.
"""
import logging
import requests
from typing import Dict, Any, List, Optional
from dataclasses import dataclass, asdict
from enum import Enum
from datetime import datetime

logger = logging.getLogger(__name__)


class FeedbackType(Enum):
    """Types of feedback for agent behavior."""
    POSITIVE = "POSITIVE"
    NEGATIVE = "NEGATIVE"
    CORRECTIVE = "CORRECTIVE"
    NEUTRAL = "NEUTRAL"


@dataclass
class FeedbackSubmission:
    """Feedback submission data."""
    agent_id: str
    feedback_type: str  # FeedbackType enum value
    feedback_text: str
    context: Optional[str] = None
    action_id: Optional[str] = None
    behavior_category: Optional[str] = None


@dataclass
class AgentFeedback:
    """Agent feedback response data."""
    id: int
    agent_id: str
    agent_name: Optional[str]
    feedback_type: str
    feedback_text: str
    context: Optional[str]
    action_id: Optional[str]
    trust_impact: Optional[int]
    provided_by: str
    timestamp: str
    processed: bool
    behavior_category: Optional[str]
    reinforcement_weight: Optional[float]


class FeedbackClientService:
    """Service for agent feedback API communication."""
    
    def __init__(self, api_base_url: str, keycloak_service):
        """
        Initialize feedback client service.
        
        Args:
            api_base_url: Base URL for the Sentrius API
            keycloak_service: Keycloak service for authentication
        """
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
    
    def submit_feedback(
        self, 
        agent_id: str,
        feedback_type: FeedbackType,
        feedback_text: str,
        context: Optional[str] = None,
        action_id: Optional[str] = None,
        behavior_category: Optional[str] = None
    ) -> AgentFeedback:
        """
        Submit feedback for an agent.
        
        Args:
            agent_id: Agent identifier
            feedback_type: Type of feedback (POSITIVE, NEGATIVE, CORRECTIVE, NEUTRAL)
            feedback_text: Detailed feedback text
            context: Optional context about when the feedback applies
            action_id: Optional ID of the specific action being rated
            behavior_category: Optional category for the behavior (e.g., 'communication', 'accuracy')
            
        Returns:
            AgentFeedback object with submission details
        """
        url = f"{self.api_base_url}/api/v1/feedback/submit"
        headers = self._get_auth_headers()
        
        submission = FeedbackSubmission(
            agent_id=agent_id,
            feedback_type=feedback_type.value,
            feedback_text=feedback_text,
            context=context,
            action_id=action_id,
            behavior_category=behavior_category
        )
        
        try:
            response = self.session.post(
                url,
                headers=headers,
                json=asdict(submission)
            )
            response.raise_for_status()
            feedback_data = response.json()
            return AgentFeedback(**feedback_data)
        except requests.RequestException as e:
            logger.error(f"Feedback submission failed: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise
    
    def get_feedback_for_agent(
        self,
        agent_id: str,
        start: Optional[str] = None,
        end: Optional[str] = None
    ) -> List[AgentFeedback]:
        """
        Get feedback history for an agent.
        
        Args:
            agent_id: Agent identifier
            start: Optional start timestamp (ISO format)
            end: Optional end timestamp (ISO format)
            
        Returns:
            List of AgentFeedback objects
        """
        url = f"{self.api_base_url}/api/v1/feedback/agent/{agent_id}"
        headers = self._get_auth_headers()
        
        params = {}
        if start:
            params['start'] = start
        if end:
            params['end'] = end
        
        try:
            response = self.session.get(url, headers=headers, params=params)
            response.raise_for_status()
            feedback_list = response.json()
            return [AgentFeedback(**f) for f in feedback_list]
        except requests.RequestException as e:
            logger.error(f"Failed to get feedback for agent {agent_id}: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise
    
    def get_feedback_by_type(
        self,
        agent_id: str,
        feedback_type: FeedbackType
    ) -> List[AgentFeedback]:
        """
        Get feedback for an agent filtered by type.
        
        Args:
            agent_id: Agent identifier
            feedback_type: Type of feedback to retrieve
            
        Returns:
            List of AgentFeedback objects
        """
        url = f"{self.api_base_url}/api/v1/feedback/agent/{agent_id}/type/{feedback_type.value}"
        headers = self._get_auth_headers()
        
        try:
            response = self.session.get(url, headers=headers)
            response.raise_for_status()
            feedback_list = response.json()
            return [AgentFeedback(**f) for f in feedback_list]
        except requests.RequestException as e:
            logger.error(f"Failed to get {feedback_type.value} feedback for agent {agent_id}: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise
    
    def get_feedback_statistics(
        self,
        agent_id: str,
        days: int = 30
    ) -> Dict[str, Any]:
        """
        Get aggregated feedback statistics for an agent.
        
        Args:
            agent_id: Agent identifier
            days: Number of days to include in statistics
            
        Returns:
            Dictionary with feedback statistics
        """
        url = f"{self.api_base_url}/api/v1/feedback/agent/{agent_id}/statistics"
        headers = self._get_auth_headers()
        params = {'days': days}
        
        try:
            response = self.session.get(url, headers=headers, params=params)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            logger.error(f"Failed to get feedback statistics for agent {agent_id}: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise
    
    def delete_feedback(self, feedback_id: int) -> bool:
        """
        Delete a feedback entry.
        
        Args:
            feedback_id: ID of the feedback to delete
            
        Returns:
            True if deletion was successful
        """
        url = f"{self.api_base_url}/api/v1/feedback/{feedback_id}"
        headers = self._get_auth_headers()
        
        try:
            response = self.session.delete(url, headers=headers)
            response.raise_for_status()
            result = response.json()
            return result.get('deleted', False)
        except requests.RequestException as e:
            logger.error(f"Failed to delete feedback {feedback_id}: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise
    
    def get_recent_feedback(self, hours: int = 24) -> List[AgentFeedback]:
        """
        Get recent feedback across all agents.
        
        Args:
            hours: Number of hours to look back
            
        Returns:
            List of AgentFeedback objects
        """
        url = f"{self.api_base_url}/api/v1/feedback/recent"
        headers = self._get_auth_headers()
        params = {'hours': hours}
        
        try:
            response = self.session.get(url, headers=headers, params=params)
            response.raise_for_status()
            feedback_list = response.json()
            return [AgentFeedback(**f) for f in feedback_list]
        except requests.RequestException as e:
            logger.error(f"Failed to get recent feedback: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response content: {e.response.text}")
            raise
