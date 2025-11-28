"""Prompt Advisor - FastAPI service for ATPL-based prompt validation."""

__version__ = "0.1.0"

# Export main components for library usage
from prompt_advisor.config import settings, Settings
from prompt_advisor.models import (
    ValidatePromptRequest,
    ValidatePromptResponse,
    CategoryRatings,
    CriteriaResponse,
    Criterion,
)
from prompt_advisor.schema_loader import ATPlSchemaLoader
from prompt_advisor.llm_evaluator import LLMEvaluator
from prompt_advisor.scoring import ScoringEngine

__all__ = [
    "__version__",
    "settings",
    "Settings",
    "ValidatePromptRequest",
    "ValidatePromptResponse",
    "CategoryRatings",
    "CriteriaResponse",
    "Criterion",
    "ATPlSchemaLoader",
    "LLMEvaluator",
    "ScoringEngine",
]
