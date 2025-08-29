"""
RSA key generation utilities for secure communication.
Equivalent to Java EphemeralKeyGen class.
"""
import base64
import logging
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.backends import default_backend
from typing import Tuple

logger = logging.getLogger(__name__)


class EphemeralKeyGen:
    """Utility class for RSA key generation and cryptographic operations."""
    
    @staticmethod
    def generate_ephemeral_rsa_keypair(key_size: int = 2048) -> Tuple:
        """
        Generate an ephemeral RSA key pair.
        
        Args:
            key_size: Size of the RSA key (default 2048)
            
        Returns:
            Tuple of (private_key, public_key)
        """
        try:
            private_key = rsa.generate_private_key(
                public_exponent=65537,
                key_size=key_size,
                backend=default_backend()
            )
            public_key = private_key.public_key()
            return private_key, public_key
        except Exception as e:
            logger.error(f"Failed to generate RSA key pair: {e}")
            raise
    
    @staticmethod
    def get_base64_public_key(public_key) -> str:
        """
        Convert public key to base64 encoded string.
        
        Args:
            public_key: RSA public key object
            
        Returns:
            Base64 encoded public key string
        """
        try:
            pem = public_key.public_key_pem() if hasattr(public_key, 'public_key_pem') else \
                  public_key.public_bytes(
                      encoding=serialization.Encoding.PEM,
                      format=serialization.PublicFormat.SubjectPublicKeyInfo
                  )
            return base64.b64encode(pem).decode('utf-8')
        except Exception as e:
            logger.error(f"Failed to encode public key to base64: {e}")
            raise
    
    @staticmethod
    def decrypt_rsa_with_private_key(encrypted_secret: str, private_key) -> str:
        """
        Decrypt RSA encrypted data using private key.
        
        Args:
            encrypted_secret: Base64 encoded encrypted data
            private_key: RSA private key object
            
        Returns:
            Decrypted string
        """
        try:
            encrypted_data = base64.b64decode(encrypted_secret)
            decrypted_bytes = private_key.decrypt(
                encrypted_data,
                padding.PKCS1v15()
            )
            return decrypted_bytes.decode('utf-8')
        except Exception as e:
            logger.error(f"Failed to decrypt RSA data: {e}")
            raise
    
    @staticmethod
    def encrypt_rsa_with_public_key(data: str, public_key) -> str:
        """
        Encrypt data using RSA public key.
        
        Args:
            data: String data to encrypt
            public_key: RSA public key object
            
        Returns:
            Base64 encoded encrypted data
        """
        try:
            data_bytes = data.encode('utf-8')
            encrypted_bytes = public_key.encrypt(
                data_bytes,
                padding.PKCS1v15()
            )
            return base64.b64encode(encrypted_bytes).decode('utf-8')
        except Exception as e:
            logger.error(f"Failed to encrypt RSA data: {e}")
            raise