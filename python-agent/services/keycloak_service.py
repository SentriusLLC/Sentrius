"""
Keycloak service for handling authentication with Keycloak server.
Equivalent to Java KeycloakService class.
"""
import jwt
import requests
import logging
from typing import Optional, Dict, Any
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
import base64

logger = logging.getLogger(__name__)


class KeycloakService:
    """Service for Keycloak authentication and token management."""
    
    def __init__(self, server_url: str, realm: str, client_id: str, client_secret: str):
        self.server_url = server_url.rstrip('/')
        self.realm = realm
        self.client_id = client_id
        self.client_secret = client_secret
        self.public_keys_cache = {}
        
    def get_keycloak_token(self) -> str:
        """Get access token from Keycloak using client credentials."""
        token_url = f"{self.server_url}/realms/{self.realm}/protocol/openid-connect/token"
        
        data = {
            'grant_type': 'client_credentials',
            'client_id': self.client_id,
            'client_secret': self.client_secret
        }
        
        try:
            response = requests.post(token_url, data=data)
            response.raise_for_status()
            token_data = response.json()
            return token_data['access_token']
        except requests.RequestException as e:
            logger.error(f"Failed to get Keycloak token: {e}")
            raise
    
    def validate_jwt(self, token: str) -> bool:
        """Validate a JWT using Keycloak public key."""
        try:
            # Extract kid from JWT header
            kid = self._extract_kid(token)
            if not kid:
                logger.error("No 'kid' found in JWT header")
                return False
                
            # Get public key for kid
            public_key = self._get_public_key(kid)
            if not public_key:
                logger.error(f"No public key found for 'kid': {kid}")
                return False
                
            # Validate JWT
            jwt.decode(token, public_key, algorithms=['RS256'])
            return True
        except Exception as e:
            logger.error(f"JWT validation failed: {e}")
            return False
    
    def extract_agent_id(self, token: str) -> Optional[str]:
        """Extract the client ID (agent identity) from a valid JWT."""
        try:
            decoded = jwt.decode(token, options={"verify_signature": False})
            return decoded.get('azp') or decoded.get('client_id')
        except Exception as e:
            logger.error(f"Failed to extract agent ID: {e}")
            return None
    
    def extract_username(self, token: str) -> Optional[str]:
        """Extract username from JWT token."""
        try:
            decoded = jwt.decode(token, options={"verify_signature": False})
            return decoded.get('preferred_username') or decoded.get('sub')
        except Exception as e:
            logger.error(f"Failed to extract username: {e}")
            return None
    
    def _extract_kid(self, token: str) -> Optional[str]:
        """Extract the 'kid' (Key ID) from JWT header."""
        try:
            header = jwt.get_unverified_header(token)
            return header.get('kid')
        except Exception as e:
            logger.error(f"Failed to extract kid: {e}")
            return None
    
    def _get_public_key(self, kid: str):
        """Get public key for the given kid."""
        if kid in self.public_keys_cache:
            return self.public_keys_cache[kid]
            
        try:
            # Fetch JWKS from Keycloak
            jwks_url = f"{self.server_url}/realms/{self.realm}/protocol/openid-connect/certs"
            response = requests.get(jwks_url)
            response.raise_for_status()
            jwks = response.json()
            
            # Find the key with matching kid
            for key_data in jwks.get('keys', []):
                if key_data.get('kid') == kid:
                    # Convert JWK to public key object
                    public_key = self._jwk_to_public_key(key_data)
                    self.public_keys_cache[kid] = public_key
                    return public_key
                    
        except Exception as e:
            logger.error(f"Failed to fetch public key: {e}")
            
        return None
    
    def _jwk_to_public_key(self, jwk_data: Dict[str, Any]):
        """Convert JWK data to cryptography public key object."""
        try:
            from cryptography.hazmat.primitives.asymmetric.rsa import RSAPublicNumbers
            from cryptography.hazmat.backends import default_backend
            
            n = int.from_bytes(
                base64.urlsafe_b64decode(jwk_data['n'] + '=='), 
                byteorder='big'
            )
            e = int.from_bytes(
                base64.urlsafe_b64decode(jwk_data['e'] + '=='), 
                byteorder='big'
            )
            
            public_numbers = RSAPublicNumbers(e, n)
            return public_numbers.public_key(default_backend())
        except Exception as e:
            logger.error(f"Failed to convert JWK to public key: {e}")
            return None