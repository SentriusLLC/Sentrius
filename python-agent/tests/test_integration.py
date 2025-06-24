"""
Integration test with mocked services to validate agent functionality.
"""
import unittest
import os
import sys
from unittest.mock import Mock, patch

# Add parent directory to Python path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from services.config import SentriusAgentConfig, KeycloakConfig, AgentConfig, LLMConfig
from services.sentrius_agent import SentriusAgent


class TestSentriusAgentIntegration(unittest.TestCase):
    """Test the complete agent integration with mocked services."""
    
    def setUp(self):
        """Set up test environment."""
        self.config = SentriusAgentConfig(
            keycloak=KeycloakConfig(
                server_url='http://localhost:8080',
                realm='test-realm',
                client_id='test-client',
                client_secret='test-secret'
            ),
            agent=AgentConfig(name_prefix='test-agent'),
            llm=LLMConfig()
        )
    
    @patch('services.sentrius_agent.KeycloakService')
    @patch('services.sentrius_agent.AgentClientService')
    def test_sentrius_agent_initialization(self, mock_agent_client, mock_keycloak):
        """Test SentriusAgent initialization."""
        # Mock the services
        mock_keycloak_instance = Mock()
        mock_keycloak.return_value = mock_keycloak_instance
        
        mock_agent_client_instance = Mock()
        mock_agent_client.return_value = mock_agent_client_instance
        
        # Create agent
        agent = SentriusAgent(self.config)
        
        # Verify initialization
        self.assertTrue(agent.agent_id.startswith('test-agent'))
        self.assertFalse(agent.running)
        
        # Verify services were created
        mock_keycloak.assert_called_once()
        mock_agent_client.assert_called_once()
    
    @patch('services.sentrius_agent.KeycloakService')
    @patch('services.sentrius_agent.AgentClientService')
    def test_sentrius_agent_start_stop(self, mock_agent_client, mock_keycloak):
        """Test agent start and stop functionality."""
        # Mock the services
        mock_keycloak_instance = Mock()
        mock_keycloak.return_value = mock_keycloak_instance
        
        mock_agent_client_instance = Mock()
        mock_agent_client_instance.register_agent.return_value = {'status': 'success'}
        mock_agent_client_instance.submit_provenance.return_value = {'status': 'success'}
        mock_agent_client.return_value = mock_agent_client_instance
        
        # Create and start agent
        agent = SentriusAgent(self.config)
        agent.start()
        
        # Verify agent is running
        self.assertTrue(agent.running)
        
        # Verify registration was called
        mock_agent_client_instance.register_agent.assert_called_once()
        
        # Stop agent
        agent.stop()
        
        # Verify agent is stopped
        self.assertFalse(agent.running)


if __name__ == '__main__':
    unittest.main()