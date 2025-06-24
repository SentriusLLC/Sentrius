"""
Integration test with mocked services to validate agent functionality.
"""
import unittest
import os
import sys
from unittest.mock import Mock, patch, MagicMock
import tempfile
import yaml

# Add parent directory to Python path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from services.config import SentriusAgentConfig, KeycloakConfig, AgentConfig, LLMConfig
from services.sentrius_agent import SentriusAgent
from agents.sql_agent.sql_agent import SQLAgent


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
    
    def test_sql_agent_initialization_with_config(self):
        """Test SQL agent initialization with configuration."""
        # Create temporary config file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.yaml', delete=False) as f:
            config_data = {
                'keycloak': {
                    'server_url': 'http://localhost:8080',
                    'realm': 'test-realm',
                    'client_id': 'test-client',
                    'client_secret': 'test-secret'
                },
                'agent': {
                    'name_prefix': 'test-sql-agent',
                    'agent_type': 'python',
                    'callback_url': 'http://localhost:8081',
                    'api_url': 'http://localhost:8080',
                    'heartbeat_interval': 30
                },
                'llm': {
                    'enabled': False
                },
                'database_url': 'sqlite:///test.db',
                'questions_file': None,
                'model_name': 'gpt-3.5-turbo'
            }
            yaml.dump(config_data, f)
            config_path = f.name
        
        try:
            # Create SQL agent (this will initialize but not start)
            sql_agent = SQLAgent(config_path=config_path)
            
            # Verify initialization
            self.assertEqual(sql_agent.name, 'SQL Agent')
            self.assertIsNotNone(sql_agent.sentrius_agent)
            self.assertIsNotNone(sql_agent.sql_config)
            
        finally:
            # Clean up
            os.unlink(config_path)
    
    @patch.dict(os.environ, {'OPENAI_API_KEY': 'test-key'})
    @patch('agents.sql_agent.sql_agent.SQLDatabase')
    @patch('agents.sql_agent.sql_agent.ChatOpenAI')
    @patch('agents.sql_agent.sql_agent.SQLDatabaseSequentialChain')
    def test_sql_agent_with_database_config(self, mock_chain, mock_llm, mock_db):
        """Test SQL agent with database configuration."""
        # Mock the database components
        mock_db_instance = Mock()
        mock_db.from_uri.return_value = mock_db_instance
        
        mock_llm_instance = Mock()
        mock_llm.return_value = mock_llm_instance
        
        mock_chain_instance = Mock()
        mock_chain.from_llm.return_value = mock_chain_instance
        
        # Create temporary config file with database URL
        with tempfile.NamedTemporaryFile(mode='w', suffix='.yaml', delete=False) as f:
            config_data = {
                'keycloak': {
                    'server_url': 'http://localhost:8080',
                    'realm': 'test-realm',
                    'client_id': 'test-client',
                    'client_secret': 'test-secret'
                },
                'agent': {
                    'name_prefix': 'test-sql-agent'
                },
                'llm': {
                    'enabled': False
                },
                'database_url': 'sqlite:///test.db',
                'questions_file': None,
                'model_name': 'gpt-3.5-turbo'
            }
            yaml.dump(config_data, f)
            config_path = f.name
        
        try:
            # Create SQL agent
            sql_agent = SQLAgent(config_path=config_path)
            
            # Verify database components were initialized
            mock_db.from_uri.assert_called_once_with('sqlite:///test.db')
            mock_llm.assert_called_once_with(model='gpt-3.5-turbo', openai_api_key='test-key')
            mock_chain.from_llm.assert_called_once_with(
                mock_llm_instance, mock_db_instance, verbose=True
            )
            
        finally:
            # Clean up
            os.unlink(config_path)


if __name__ == '__main__':
    unittest.main()