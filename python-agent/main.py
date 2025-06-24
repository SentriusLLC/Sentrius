import argparse
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def main():
    parser = argparse.ArgumentParser(description="Run selected Sentrius Python agent.")
    parser.add_argument(
        "agent",
        choices=[],  # No agents available yet - SQL agent removed per feedback
        help="Select the agent to run."
    )
    parser.add_argument(
        "--config",
        help="Path to agent configuration file",
        default=None
    )
    
    args = parser.parse_args()

    # No agents available currently - SQL agent was removed
    logger.error("No agents are currently available. SQL agent was removed as it won't have direct database access.")
    logger.info("All interactions are through APIs once JWT is obtained, working via DTOs and LLM proxy.")
    return 1


if __name__ == "__main__":
    exit(main())
