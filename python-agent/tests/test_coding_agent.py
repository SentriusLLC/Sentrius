"""
Unit tests for the Coding Agent
"""
import unittest
import json
import os
import sys
from unittest.mock import MagicMock, patch

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from utils.config_manager import ConfigManager
from agents.coding.coding_agent import CodingAgent


class TestCodingAgent(unittest.TestCase):
    """Test cases for Coding Agent functionality."""
    
    def setUp(self):
        """Set up test fixtures."""
        # Create a test config manager with test mode enabled
        os.environ['TEST_MODE'] = 'true'
        self.config_manager = ConfigManager('application.properties')
        
    def test_agent_initialization(self):
        """Test that the agent initializes correctly."""
        agent = CodingAgent(self.config_manager)
        
        self.assertEqual(agent.name, 'coding-agent')
        self.assertIsNotNone(agent.agent_definition)
        self.assertTrue(agent.test_mode)
    
    def test_execute_task_test_mode(self):
        """Test task execution in test mode."""
        agent = CodingAgent(self.config_manager)
        
        task_data = {
            "operation": "create_pr",
            "repo": "test/repo",
            "title": "Test PR",
            "description": "Test description"
        }
        
        result = agent.execute_task(task_data)
        
        self.assertEqual(result['status'], 'test_mode')
        self.assertIn('message', result)
        self.assertEqual(result['task_data'], task_data)
    
    def test_sanitize_branch_name(self):
        """Test branch name sanitization."""
        agent = CodingAgent(self.config_manager)
        
        # Test with special characters
        result = agent._sanitize_branch_name("Fix: Bug in Feature #123")
        self.assertNotIn(' ', result)
        self.assertNotIn(':', result)
        self.assertNotIn('#', result)
        
        # Test with consecutive hyphens
        result = agent._sanitize_branch_name("Fix---Multiple---Hyphens")
        self.assertNotIn('---', result)
        
        # Test length limit
        long_name = "a" * 100
        result = agent._sanitize_branch_name(long_name)
        self.assertLessEqual(len(result), 50)
    
    def test_build_coding_prompt(self):
        """Test coding prompt construction."""
        agent = CodingAgent(self.config_manager)
        
        title = "Add new feature"
        description = "Implement feature X with Y"
        context = {"language": "Python", "framework": "Flask"}
        
        prompt = agent._build_coding_prompt(title, description, context)
        
        self.assertIn(title, prompt)
        self.assertIn(description, prompt)
        self.assertIn("Python", prompt)
        self.assertIn("Flask", prompt)
        self.assertIn("JSON format", prompt)
    
    def test_parse_llm_code_response_valid_json(self):
        """Test parsing valid JSON response from LLM."""
        agent = CodingAgent(self.config_manager)
        
        response = '''
        Here is the code:
        {
            "files": [
                {
                    "path": "test.py",
                    "content": "print('hello')",
                    "operation": "create"
                }
            ],
            "explanation": "Created a test file"
        }
        '''
        
        result = agent._parse_llm_code_response(response)
        
        self.assertIn('files', result)
        self.assertIn('explanation', result)
        self.assertEqual(len(result['files']), 1)
        self.assertEqual(result['files'][0]['path'], 'test.py')
    
    def test_parse_llm_code_response_invalid_json(self):
        """Test parsing invalid JSON response from LLM."""
        agent = CodingAgent(self.config_manager)
        
        response = "This is just plain text without JSON"
        
        result = agent._parse_llm_code_response(response)
        
        self.assertIn('files', result)
        self.assertIn('explanation', result)
        self.assertEqual(len(result['files']), 0)
        self.assertEqual(result['explanation'], response)
    
    def test_get_agent_info(self):
        """Test agent information retrieval."""
        agent = CodingAgent(self.config_manager)
        
        info = agent.get_agent_info()
        
        self.assertEqual(info['name'], 'coding')
        self.assertEqual(info['type'], 'automation')
        self.assertIn('capabilities', info)
        self.assertIn('code_generation', info['capabilities'])
        self.assertIn('pull_request_creation', info['capabilities'])
    
    def test_invalid_operation(self):
        """Test handling of invalid operations."""
        agent = CodingAgent(self.config_manager)
        
        task_data = {
            "operation": "invalid_operation"
        }
        
        # In test mode, operation validation still happens
        # but the actual execution is skipped
        result = agent.execute_task(task_data)
        
        # In test mode, we should still get a response
        # but in production mode, this would raise ValueError
        self.assertEqual(result['status'], 'test_mode')
    
    def test_missing_required_fields(self):
        """Test handling of missing required fields."""
        agent = CodingAgent(self.config_manager)
        
        # Missing issue_key for JIRA operation
        task_data = {
            "operation": "handle_jira_issue"
            # issue_key is missing
        }
        
        # In test mode, this should not raise an error
        # The real validation would happen in production mode
        result = agent.execute_task(task_data)
        self.assertEqual(result['status'], 'test_mode')


class TestCodingAgentIntegration(unittest.TestCase):
    """Integration tests for Coding Agent (require external services)."""
    
    @unittest.skipIf(os.environ.get('TEST_MODE') == 'true', 
                     "Skipping integration tests in test mode")
    def test_full_workflow(self):
        """Test full coding workflow with external services."""
        # This test requires:
        # - Keycloak running
        # - Integration proxy running
        # - GitHub MCP server available
        # - LLM proxy running
        
        # Would be implemented when services are available
        pass


if __name__ == '__main__':
    # Run tests
    unittest.main(verbosity=2)
