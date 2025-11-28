"""Scoring logic for prompt validation."""
from typing import Dict
import logging

logger = logging.getLogger(__name__)


class ScoringEngine:
    """Engine for calculating weighted scores from category ratings."""
    
    def __init__(
        self,
        weight_purpose: int = 15,
        weight_safety: int = 30,
        weight_compliance: int = 25,
        weight_provenance: int = 15,
        weight_autonomy: int = 15
    ):
        """
        Initialize scoring engine with category weights.
        
        Weights should sum to 100 for proper percentage calculation.
        """
        self.weights = {
            "purpose": weight_purpose,
            "safety": weight_safety,
            "compliance": weight_compliance,
            "provenance": weight_provenance,
            "autonomy": weight_autonomy
        }
        
        total_weight = sum(self.weights.values())
        if total_weight != 100:
            logger.warning(f"Category weights sum to {total_weight}, not 100. Normalizing.")
            # Normalize weights to sum to 100
            factor = 100 / total_weight
            self.weights = {k: int(v * factor) for k, v in self.weights.items()}
    
    def calculate_score(self, ratings: Dict[str, int]) -> int:
        """
        Calculate overall score from category ratings.
        
        Args:
            ratings: Dictionary mapping category names to ratings (0-10)
        
        Returns:
            Overall score (0-100)
        """
        total_score = 0
        
        for category, rating in ratings.items():
            if category in self.weights:
                # Convert rating (0-10) to percentage (0-100) and apply weight
                category_contribution = (rating / 10.0) * self.weights[category]
                total_score += category_contribution
        
        return int(round(total_score))
    
    def get_weights(self) -> Dict[str, int]:
        """Get current category weights."""
        return self.weights.copy()
