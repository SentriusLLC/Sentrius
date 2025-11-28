"""ATPL schema loader and parser."""
import httpx
from typing import Dict, Any, Optional
import logging

logger = logging.getLogger(__name__)


class ATPlSchemaLoader:
    """Loader for ATPL schema from URL."""
    
    def __init__(self, schema_url: str):
        self.schema_url = schema_url
        self._schema: Optional[Dict[str, Any]] = None
        self._criteria: Optional[Dict[str, str]] = None
    
    async def load_schema(self) -> Dict[str, Any]:
        """Load ATPL schema from URL."""
        if self._schema is not None:
            return self._schema
        
        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(self.schema_url, timeout=10.0)
                response.raise_for_status()
                self._schema = response.json()
                logger.info(f"Successfully loaded ATPL schema from {self.schema_url}")
                return self._schema
        except httpx.HTTPError as e:
            logger.error(f"Failed to load ATPL schema: {e}")
            # Return default schema if loading fails
            return self._get_default_schema()
        except Exception as e:
            logger.error(f"Unexpected error loading ATPL schema: {e}")
            return self._get_default_schema()
    
    def _get_default_schema(self) -> Dict[str, Any]:
        """Return a default schema if loading fails."""
        return {
            "criteria": {
                "purpose": {
                    "name": "Purpose Clarity",
                    "description": "Clear definition of intent and expected outcomes"
                },
                "safety": {
                    "name": "Safety & Prohibited Content",
                    "description": "Absence of harmful, illegal, or prohibited content"
                },
                "compliance": {
                    "name": "Data Sensitivity & Compliance",
                    "description": "Proper handling of sensitive data and regulatory compliance"
                },
                "provenance": {
                    "name": "Trust & Provenance",
                    "description": "Traceability and authenticity of information sources"
                },
                "autonomy": {
                    "name": "Agent Autonomy Bounds",
                    "description": "Appropriate limits on autonomous agent actions"
                }
            }
        }
    
    async def get_criteria(self) -> Dict[str, str]:
        """Get criteria definitions from schema."""
        if self._criteria is not None:
            return self._criteria
        
        schema = await self.load_schema()
        self._criteria = {}
        
        # Extract criteria from schema
        if "criteria" in schema:
            for key, value in schema["criteria"].items():
                if isinstance(value, dict):
                    self._criteria[key] = value.get("description", value.get("name", key))
                else:
                    self._criteria[key] = str(value)
        
        return self._criteria
