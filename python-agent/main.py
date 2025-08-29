import argparse
import logging
import sys
from pathlib import Path

# Add the current directory to the Python path
sys.path.append(str(Path(__file__).parent))

from utils.config_manager import ConfigManager
from agents.chat_helper.chat_helper_agent import ChatHelperAgent
from agents.mcp.mcp_agent import MCPAgent

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

AVAILABLE_AGENTS = {
    'chat-helper': ChatHelperAgent,
    'mcp': MCPAgent,
}


def main():
    parser = argparse.ArgumentParser(description="Run selected Sentrius Python agent.")
    parser.add_argument(
        "agent",
        choices=list(AVAILABLE_AGENTS.keys()),
        help="Select the agent to run."
    )
    parser.add_argument(
        "--config",
        help="Path to agent configuration properties file",
        default="application.properties"
    )
    parser.add_argument(
        "--task-data",
        help="JSON string with task data for the agent",
        default=None
    )
    
    args = parser.parse_args()

    try:
        # Load configuration
        config_manager = ConfigManager(args.config)
        
        # Check if the requested agent is enabled
        if not config_manager.is_agent_enabled(args.agent.replace('-', '.')):
            logger.error(f"Agent '{args.agent}' is not enabled in configuration")
            return 1
        
        # Initialize and run the agent
        agent_class = AVAILABLE_AGENTS[args.agent]
        agent = agent_class(config_manager)
        
        logger.info(f"Starting {args.agent} agent...")
        
        # Parse task data if provided
        task_data = None
        if args.task_data:
            import json
            task_data = json.loads(args.task_data)
        
        # Execute the agent task
        result = agent.execute_task(task_data)
        
        logger.info(f"Agent execution completed successfully")
        logger.info(f"Result: {result}")
        
        return 0
        
    except Exception as e:
        logger.error(f"Error running agent: {e}")
        return 1


if __name__ == "__main__":
    exit(main())
