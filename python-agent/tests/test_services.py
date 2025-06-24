"""
Basic tests for Sentrius Python Agent services.
"""
import unittest
import os
import sys
from unittest.mock import Mock, patch, MagicMock

# Add parent directory to Python path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from services.config import SentriusAgentConfig, KeycloakConfig, AgentConfig, LLMConfig
from services.keycloak_service import KeycloakService
from services.key_service import EphemeralKeyGen


class TestSentriusAgentConfig(unittest.TestCase):
    """Test configuration management."""
    
    def test_config_from_env(self):
        """Test loading configuration from environment variables."""
        with patch.dict(os.environ, {
            'KEYCLOAK_SERVER_URL': 'http://test:8080',
            'KEYCLOAK_REALM': 'test-realm',
            'KEYCLOAK_CLIENT_ID': 'test-client',
            'KEYCLOAK_CLIENT_SECRET': 'test-secret',
            'AGENT_NAME_PREFIX': 'test-agent'
        }):
            config = SentriusAgentConfig.from_env()
            
            self.assertEqual(config.keycloak.server_url, 'http://test:8080')
            self.assertEqual(config.keycloak.realm, 'test-realm')
            self.assertEqual(config.keycloak.client_id, 'test-client')
            self.assertEqual(config.keycloak.client_secret, 'test-secret')
            self.assertEqual(config.agent.name_prefix, 'test-agent')
    
    def test_config_to_dict(self):
        """Test configuration conversion to dictionary."""
        config = SentriusAgentConfig(
            keycloak=KeycloakConfig(
                server_url='http://test:8080',
                realm='test-realm',
                client_id='test-client',
                client_secret='test-secret'
            ),
            agent=AgentConfig(name_prefix='test-agent'),
            llm=LLMConfig()
        )
        
        config_dict = config.to_dict()
        
        self.assertIn('keycloak', config_dict)
        self.assertIn('agent', config_dict)
        self.assertIn('llm', config_dict)
        self.assertEqual(config_dict['keycloak']['server_url'], 'http://test:8080')


class TestEphemeralKeyGen(unittest.TestCase):
    """Test RSA key generation utilities."""
    
    def test_generate_keypair(self):
        """Test RSA key pair generation."""
        private_key, public_key = EphemeralKeyGen.generate_ephemeral_rsa_keypair()
        
        self.assertIsNotNone(private_key)
        self.assertIsNotNone(public_key)
    
    def test_base64_public_key(self):
        """Test base64 encoding of public key."""
        private_key, public_key = EphemeralKeyGen.generate_ephemeral_rsa_keypair()
        base64_key = EphemeralKeyGen.get_base64_public_key(public_key)
        
        self.assertIsInstance(base64_key, str)
        self.assertTrue(len(base64_key) > 0)
    
    def test_encrypt_decrypt(self):
        """Test RSA encryption and decryption."""
        private_key, public_key = EphemeralKeyGen.generate_ephemeral_rsa_keypair()
        test_data = "Hello, World!"
        
        # Encrypt with public key
        encrypted_data = EphemeralKeyGen.encrypt_rsa_with_public_key(test_data, public_key)
        
        # Decrypt with private key
        decrypted_data = EphemeralKeyGen.decrypt_rsa_with_private_key(encrypted_data, private_key)
        
        self.assertEqual(test_data, decrypted_data)


class TestKeycloakService(unittest.TestCase):
    """Test Keycloak service functionality."""
    
    def setUp(self):
        """Set up test environment."""
        self.keycloak_service = KeycloakService(
            server_url='http://test:8080',
            realm='test-realm',
            client_id='test-client',
            client_secret='test-secret'
        )
    
    @patch('requests.post')
    def test_get_keycloak_token(self, mock_post):
        """Test getting Keycloak token."""
        # Mock successful response
        mock_response = Mock()
        mock_response.json.return_value = {'access_token': 'test-token'}
        mock_response.raise_for_status.return_value = None
        mock_post.return_value = mock_response
        
        token = self.keycloak_service.get_keycloak_token()
        
        self.assertEqual(token, 'test-token')
        mock_post.assert_called_once()
    
    def test_extract_agent_id(self):
        """Test extracting agent ID from JWT token."""
        # Create a mock JWT token (not signed, just for testing)
        import base64
        import json
        
        header = {'typ': 'JWT', 'alg': 'RS256'}
        payload = {'azp': 'test-agent-id', 'exp': 9999999999}
        
        header_b64 = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip('=')
        payload_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip('=')
        
        mock_token = f"{header_b64}.{payload_b64}.mock-signature"
        
        agent_id = self.keycloak_service.extract_agent_id(mock_token)
        
        self.assertEqual(agent_id, 'test-agent-id')


if __name__ == '__main__':
    unittest.main()