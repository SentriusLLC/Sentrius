"""Configuration management for prompt_advisor service."""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings."""
    
    # Server configuration
    host: str = "0.0.0.0"
    port: int = 8000
    
    # ATPL schema URL
    atpl_schema_url: str = "https://raw.githubusercontent.com/SentriusLLC/atpl/main/atpl.schema.json"
    
    # LLM evaluator configuration
    llm_endpoint: str = ""
    llm_api_key: str = ""
    llm_model: str = "gpt-4"
    llm_enabled: bool = False
    
    # Identity management configuration
    # Format: "Header-Name:header-value,Another-Header:another-value"
    llm_custom_headers: str = ""
    
    # Keycloak configuration for native token management
    keycloak_url: str = ""
    keycloak_realm: str = "sentrius"
    keycloak_client_id: str = "prompt-advisor"
    keycloak_client_secret: str = ""
    keycloak_verify_ssl: bool = True
    
    # Scoring weights (must sum to 100)
    weight_purpose: int = 15
    weight_safety: int = 30
    weight_compliance: int = 25
    weight_provenance: int = 15
    weight_autonomy: int = 15
    
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8"
    )


settings = Settings()
