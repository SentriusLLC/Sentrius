"""
Test configuration manager functionality.
"""
import unittest
import tempfile
import os
from pathlib import Path
from utils.config_manager import ConfigManager


class TestConfigManager(unittest.TestCase):
    """Test the ConfigManager class."""
    
    def setUp(self):
        """Set up test configuration files."""
        # Create temporary directory for test files
        self.test_dir = tempfile.mkdtemp()
        self.properties_file = os.path.join(self.test_dir, "test.properties")
        self.yaml_file = os.path.join(self.test_dir, "test-agent.yaml")
        
        # Create test properties file
        properties_content = """
# Test properties
test.mode=true
keycloak.realm=test-realm
keycloak.base-url=${KEYCLOAK_URL:http://localhost:8180}
agent.name.prefix=test-agent
agent.test.config=test-agent.yaml
agent.test.enabled=true
"""
        with open(self.properties_file, 'w') as f:
            f.write(properties_content)
        
        # Create test YAML file
        yaml_content = """
description: "Test agent for unit tests"
context: |
  You are a test agent used for unit testing.
  Always return structured responses for testing.
"""
        with open(self.yaml_file, 'w') as f:
            f.write(yaml_content)
    
    def tearDown(self):
        """Clean up test files."""
        import shutil
        shutil.rmtree(self.test_dir)
    
    def test_load_properties(self):
        """Test loading properties file."""
        config_manager = ConfigManager(self.properties_file)
        
        self.assertEqual(config_manager.get_property('test.mode'), 'true')
        self.assertEqual(config_manager.get_property('keycloak.realm'), 'test-realm')
        self.assertEqual(config_manager.get_property('agent.name.prefix'), 'test-agent')
    
    def test_env_var_substitution(self):
        """Test environment variable substitution."""
        # Set environment variable
        os.environ['KEYCLOAK_URL'] = 'http://test.example.com:8080'
        
        config_manager = ConfigManager(self.properties_file)
        
        # Should use environment variable
        self.assertEqual(config_manager.get_property('keycloak.base-url'), 'http://test.example.com:8080')
        
        # Clean up
        del os.environ['KEYCLOAK_URL']
    
    def test_env_var_default(self):
        """Test environment variable default values."""
        config_manager = ConfigManager(self.properties_file)
        
        # Should use default value when env var is not set
        self.assertEqual(config_manager.get_property('keycloak.base-url'), 'http://localhost:8180')
    
    def test_agent_config_loading(self):
        """Test loading agent configuration."""
        # Change to test directory so relative paths work
        original_cwd = os.getcwd()
        os.chdir(self.test_dir)
        
        try:
            config_manager = ConfigManager("test.properties")
            
            agent_config = config_manager.get_agent_definition('test')
            self.assertIsNotNone(agent_config)
            self.assertEqual(agent_config['description'], 'Test agent for unit tests')
            self.assertIn('You are a test agent', agent_config['context'])
        finally:
            os.chdir(original_cwd)
    
    def test_enabled_agents(self):
        """Test getting enabled agents."""
        config_manager = ConfigManager(self.properties_file)
        
        enabled_agents = config_manager.get_enabled_agents()
        self.assertIn('test', enabled_agents)
        
        self.assertTrue(config_manager.is_agent_enabled('test'))
        self.assertFalse(config_manager.is_agent_enabled('nonexistent'))
    
    def test_get_configs(self):
        """Test getting different configuration sections."""
        config_manager = ConfigManager(self.properties_file)
        
        keycloak_config = config_manager.get_keycloak_config()
        self.assertEqual(keycloak_config['realm'], 'test-realm')
        self.assertEqual(keycloak_config['server_url'], 'http://localhost:8180')
        
        agent_config = config_manager.get_agent_config()
        self.assertEqual(agent_config['name_prefix'], 'test-agent')
    
    def test_missing_files(self):
        """Test handling of missing configuration files."""
        # Test with non-existent properties file
        config_manager = ConfigManager('/nonexistent/file.properties')
        
        # Should not crash and should return defaults
        self.assertEqual(len(config_manager.properties), 0)
        self.assertEqual(config_manager.get_property('missing.key', 'default'), 'default')


if __name__ == '__main__':
    unittest.main()