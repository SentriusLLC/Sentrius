#!/usr/bin/env python3
"""
Keycloak Token Refresher for Prompt Advisor Service
This script obtains and periodically refreshes an access token from Keycloak
using the client credentials flow. The token is written to a shared volume
that the prompt-advisor service can read.
"""
import os
import sys
import time
import json
import logging
import requests
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class TokenRefresher:
    def __init__(self):
        self.keycloak_url = os.getenv('KEYCLOAK_URL', 'http://sentrius-keycloak:8081')
        self.realm = os.getenv('KEYCLOAK_REALM', 'sentrius')
        self.client_id = os.getenv('KEYCLOAK_CLIENT_ID', 'prompt-advisor')
        self.client_secret = os.getenv('KEYCLOAK_CLIENT_SECRET')
        self.refresh_interval = int(os.getenv('TOKEN_REFRESH_INTERVAL', '300'))  # 5 minutes default
        self.token_file = Path(os.getenv('TOKEN_FILE_PATH', '/tmp/keycloak-token/token.txt'))
        # SSL verification - can be disabled for self-signed certs in dev environments
        self.verify_ssl = os.getenv('VERIFY_SSL', 'true').lower() in ('true', '1', 'yes')
        # Optional CA certificate path for custom certificate authorities
        self.ca_cert_path = os.getenv('CA_CERT_PATH', '')
        
        if not self.client_secret:
            logger.error("KEYCLOAK_CLIENT_SECRET environment variable is required")
            sys.exit(1)
        
        # Ensure token directory exists
        self.token_file.parent.mkdir(parents=True, exist_ok=True)
        
        self.token_url = f"{self.keycloak_url}/realms/{self.realm}/protocol/openid-connect/token"
        
    def get_access_token(self):
        """Obtain an access token using client credentials flow."""
        try:
            # Determine SSL verification setting
            verify = self.verify_ssl
            if self.ca_cert_path and os.path.exists(self.ca_cert_path):
                verify = self.ca_cert_path
            
            response = requests.post(
                self.token_url,
                data={
                    'grant_type': 'client_credentials',
                    'client_id': self.client_id,
                    'client_secret': self.client_secret
                },
                headers={'Content-Type': 'application/x-www-form-urlencoded'},
                timeout=10,
                verify=verify
            )
            
            if response.status_code == 200:
                token_data = response.json()
                access_token = token_data.get('access_token')
                expires_in = token_data.get('expires_in', 300)
                
                logger.info(f"Successfully obtained access token (expires in {expires_in}s)")
                return access_token, expires_in
            else:
                logger.error(f"Failed to obtain token: {response.status_code} - {response.text}")
                return None, None
                
        except Exception as e:
            logger.error(f"Error obtaining access token: {e}")
            return None, None
    
    def write_token(self, token):
        """Write the token to a file that the prompt-advisor service can read."""
        try:
            # Write token with atomic operation
            temp_file = self.token_file.with_suffix('.tmp')
            temp_file.write_text(f"Bearer {token}")
            temp_file.replace(self.token_file)
            
            # Set permissions to be readable by the service
            self.token_file.chmod(0o644)
            
            logger.debug(f"Token written to {self.token_file}")
            return True
        except Exception as e:
            logger.error(f"Error writing token to file: {e}")
            return False
    
    def run(self):
        """Main loop to refresh token periodically."""
        logger.info("Starting Keycloak token refresher")
        logger.info(f"Keycloak URL: {self.keycloak_url}")
        logger.info(f"Realm: {self.realm}")
        logger.info(f"Client ID: {self.client_id}")
        logger.info(f"Refresh interval: {self.refresh_interval}s")
        logger.info(f"SSL verification: {self.verify_ssl}")
        if self.ca_cert_path:
            logger.info(f"CA certificate path: {self.ca_cert_path}")
        
        while True:
            token, expires_in = self.get_access_token()
            
            if token:
                if self.write_token(token):
                    # Refresh before expiry (use 80% of expiry time)
                    sleep_time = min(
                        int(expires_in * 0.8) if expires_in else self.refresh_interval,
                        self.refresh_interval
                    )
                    logger.info(f"Next token refresh in {sleep_time}s")
                    time.sleep(sleep_time)
                else:
                    logger.warning("Failed to write token, retrying in 10s")
                    time.sleep(10)
            else:
                logger.warning("Failed to obtain token, retrying in 30s")
                time.sleep(30)


if __name__ == '__main__':
    refresher = TokenRefresher()
    refresher.run()
