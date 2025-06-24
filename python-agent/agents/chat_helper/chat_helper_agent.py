"""
Chat Helper Agent - Provides conversational AI assistance.
"""
import logging
from typing import Dict, Any, Optional
from agents.base import BaseAgent

logger = logging.getLogger(__name__)


class ChatHelperAgent(BaseAgent):
    """Agent that provides chat-based assistance using LLM integration."""
    
    def __init__(self, config_manager):
        super().__init__(config_manager)
        self.agent_definition = config_manager.get_agent_definition('chat.helper')
        if not self.agent_definition:
            raise ValueError("Chat helper agent configuration not found")
        
        logger.info(f"Initialized ChatHelperAgent: {self.agent_definition.get('description', 'No description')}")
    
    def execute_task(self, task_data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Execute chat helper task."""
        try:
            # Submit provenance for task start
            self.submit_provenance("CHAT_TASK_START", {
                "agent_type": "chat-helper",
                "task_data": task_data
            })
            
            # Process the chat request (this would integrate with LLM)
            response = self._process_chat_request(task_data)
            
            # Submit provenance for task completion
            self.submit_provenance("CHAT_TASK_COMPLETE", {
                "agent_type": "chat-helper",
                "response": response
            })
            
            return response
            
        except Exception as e:
            logger.error(f"Error executing chat helper task: {e}")
            self.submit_provenance("CHAT_TASK_ERROR", {
                "agent_type": "chat-helper",
                "error": str(e)
            })
            raise
    
    def _process_chat_request(self, task_data: Optional[Dict[str, Any]]) -> Dict[str, Any]:
        """Process chat request using agent context and LLM."""
        if not task_data:
            return {
                "previousOperation": "initialization",
                "nextOperation": "waiting_for_user_input",
                "terminalSummaryForLLM": "Chat helper agent initialized and ready",
                "responseForUser": "Hello! I'm your chat helper agent. How can I assist you today?"
            }
        
        user_message = task_data.get('message', '')
        context = self.agent_definition.get('context', '')
        
        # This would integrate with the LLM service
        # For now, return a structured response based on the agent's context
        return {
            "previousOperation": "user_message_received",
            "nextOperation": "generate_response",
            "terminalSummaryForLLM": f"User asked: {user_message}",
            "responseForUser": f"I received your message: '{user_message}'. I'm a helpful chat assistant ready to help!"
        }
    
    def get_agent_info(self) -> Dict[str, Any]:
        """Get information about this agent."""
        return {
            "name": "chat-helper",
            "type": "conversational",
            "description": self.agent_definition.get('description', ''),
            "capabilities": [
                "conversational_ai",
                "user_assistance",
                "structured_responses"
            ]
        }