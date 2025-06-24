import argparse
import logging
import os
from agents.sql_agent.sql_agent import SQLAgent

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
        choices=["sql_agent"],
        help="Select the agent to run."
    )
    parser.add_argument(
        "--config",
        help="Path to agent configuration file",
        default=None
    )
    parser.add_argument(
        "--sql-config", 
        help="Path to SQL-specific configuration file for SQL agent",
        default="agents/sql_agent/config.yaml"
    )
    
    args = parser.parse_args()

    try:
        if args.agent == "sql_agent":
            logger.info("Initializing SQL Agent with Sentrius integration...")
            
            # Use SQL-specific config if provided, otherwise fall back to main config
            config_path = args.sql_config if os.path.exists(args.sql_config) else args.config
            
            sql_agent = SQLAgent(config_path=config_path)
            sql_agent.run()
        else:
            logger.error("Unknown agent. Exiting.")
            return 1
            
    except Exception as e:
        logger.error(f"Agent execution failed: {e}")
        return 1
    
    return 0


if __name__ == "__main__":
    exit(main())
