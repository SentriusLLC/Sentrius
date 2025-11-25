"""
Example script demonstrating RLHF feedback submission for agents.
Shows how to submit feedback and retrieve feedback statistics.
"""
import logging
import sys
import os
from datetime import datetime

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from services.feedback_client_service import FeedbackClientService, FeedbackType
from services.keycloak_service import KeycloakService
from utils.config_manager import ConfigManager

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def main():
    """Main function demonstrating feedback submission."""
    
    # Load configuration
    config_manager = ConfigManager()
    config = config_manager.load_config('application.properties')
    
    # Initialize Keycloak service
    keycloak_service = KeycloakService(
        server_url=config.get('keycloak.server.url', 'http://localhost:8180'),
        realm=config.get('keycloak.realm', 'sentrius'),
        client_id=config.get('keycloak.client.id', 'sentrius-agent'),
        client_secret=config.get('keycloak.client.secret', '')
    )
    
    # Initialize feedback client service
    api_url = config.get('sentrius.api.url', 'http://localhost:8080')
    feedback_service = FeedbackClientService(api_url, keycloak_service)
    
    # Example agent ID
    agent_id = "example-agent-001"
    
    # Example 1: Submit positive feedback
    logger.info("Submitting positive feedback...")
    try:
        positive_feedback = feedback_service.submit_feedback(
            agent_id=agent_id,
            feedback_type=FeedbackType.POSITIVE,
            feedback_text="Agent provided accurate and helpful responses throughout the session.",
            behavior_category="accuracy",
            context="User session on 2024-01-15"
        )
        logger.info(f"Positive feedback submitted: ID={positive_feedback.id}")
    except Exception as e:
        logger.error(f"Failed to submit positive feedback: {e}")
    
    # Example 2: Submit corrective feedback
    logger.info("Submitting corrective feedback...")
    try:
        corrective_feedback = feedback_service.submit_feedback(
            agent_id=agent_id,
            feedback_type=FeedbackType.CORRECTIVE,
            feedback_text="Agent should provide more context when explaining technical concepts.",
            behavior_category="communication",
            context="Technical discussion about security protocols"
        )
        logger.info(f"Corrective feedback submitted: ID={corrective_feedback.id}")
    except Exception as e:
        logger.error(f"Failed to submit corrective feedback: {e}")
    
    # Example 3: Get feedback statistics
    logger.info("Retrieving feedback statistics...")
    try:
        stats = feedback_service.get_feedback_statistics(agent_id, days=30)
        logger.info("Feedback Statistics (last 30 days):")
        logger.info(f"  Positive: {stats.get('positive_count', 0)}")
        logger.info(f"  Negative: {stats.get('negative_count', 0)}")
        logger.info(f"  Corrective: {stats.get('corrective_count', 0)}")
        logger.info(f"  Neutral: {stats.get('neutral_count', 0)}")
        logger.info(f"  Total: {stats.get('total_count', 0)}")
        logger.info(f"  Average Reinforcement Weight: {stats.get('average_reinforcement_weight', 0):.2f}")
        logger.info(f"  Feedback Score: {stats.get('feedback_score', 50):.2f}")
    except Exception as e:
        logger.error(f"Failed to get feedback statistics: {e}")
    
    # Example 4: Get feedback history
    logger.info("Retrieving feedback history...")
    try:
        feedback_history = feedback_service.get_feedback_for_agent(agent_id)
        logger.info(f"Found {len(feedback_history)} feedback entries")
        for feedback in feedback_history[:5]:  # Show first 5
            logger.info(f"  [{feedback.feedback_type}] {feedback.feedback_text[:50]}... "
                       f"(provided by {feedback.provided_by} at {feedback.timestamp})")
    except Exception as e:
        logger.error(f"Failed to get feedback history: {e}")
    
    # Example 5: Get positive feedback only
    logger.info("Retrieving positive feedback only...")
    try:
        positive_feedback_list = feedback_service.get_feedback_by_type(
            agent_id, FeedbackType.POSITIVE
        )
        logger.info(f"Found {len(positive_feedback_list)} positive feedback entries")
    except Exception as e:
        logger.error(f"Failed to get positive feedback: {e}")
    
    logger.info("Feedback examples completed!")


if __name__ == "__main__":
    main()
