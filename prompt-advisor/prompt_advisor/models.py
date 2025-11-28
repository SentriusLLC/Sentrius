"""Pydantic models for request and response schemas."""
from typing import Optional, Dict, Any, List
from pydantic import BaseModel, Field


class ValidatePromptRequest(BaseModel):
    """Request model for prompt validation."""
    
    prompt: str = Field(..., description="The user prompt to validate")
    context: Optional[Dict[str, Any]] = Field(None, description="Optional context as JSON")
    schema_url: Optional[str] = Field(None, description="Optional custom schema URL")


class CategoryRatings(BaseModel):
    """Ratings for each ATPL category."""
    
    purpose: int = Field(..., ge=0, le=10, description="Purpose clarity rating (0-10)")
    safety: int = Field(..., ge=0, le=10, description="Safety/prohibited content rating (0-10)")
    compliance: int = Field(..., ge=0, le=10, description="Data sensitivity/compliance rating (0-10)")
    provenance: int = Field(..., ge=0, le=10, description="Trust and provenance rating (0-10)")
    autonomy: int = Field(..., ge=0, le=10, description="Agent autonomy bounds rating (0-10)")


class ValidatePromptResponse(BaseModel):
    """Response model for prompt validation."""
    
    score: int = Field(..., ge=0, le=100, description="Overall compliance score (0-100)")
    ratings: CategoryRatings = Field(..., description="Individual category ratings")
    explanation: str = Field(..., description="Textual justification for the ratings")
    recommendations: List[str] = Field(..., description="List of recommendations for improvement")


class Criterion(BaseModel):
    """Model for a single ATPL criterion."""
    
    name: str = Field(..., description="Name of the criterion")
    description: str = Field(..., description="Description of what this criterion measures")
    weight: int = Field(..., ge=0, le=100, description="Weight percentage for scoring")


class CriteriaResponse(BaseModel):
    """Response model for criteria listing."""
    
    criteria: List[Criterion] = Field(..., description="List of ATPL criteria")
    total_weight: int = Field(..., description="Total weight percentage (should be 100)")
