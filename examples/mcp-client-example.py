#!/usr/bin/env python3
"""
Example MCP client integration with Sentrius Python Agent

This example demonstrates how to use the integrated MCP functionality
within the existing Sentrius Python agent framework.
"""

import sys
import json
import logging
from pathlib import Path

# Add the python-agent directory to the path
python_agent_dir = Path(__file__).parent.parent / "python-agent"
sys.path.insert(0, str(python_agent_dir))

from utils.config_manager import ConfigManager
from agents.mcp.mcp_agent import MCPAgent

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def main():
    """
    Example usage of the integrated MCP agent
    """
    try:
        # Initialize configuration manager
        config_path = python_agent_dir / "application.properties"
        config_manager = ConfigManager(str(config_path))
        
        # Create MCP agent using the integrated framework
        logger.info("Creating MCP agent with Sentrius integration...")
        mcp_agent = MCPAgent(config_manager)
        
        print("=== MCP Agent Examples ===\n")
        
        # Example 1: Initialize and get capabilities
        print("1. Initializing MCP agent...")
        init_result = mcp_agent.execute_task()
        print(f"Initialization result: {json.dumps(init_result, indent=2)}\n")
        
        # Example 2: Ping test
        print("2. Testing connectivity...")
        ping_result = mcp_agent.execute_task({"operation": "ping"})
        print(f"Ping result: {json.dumps(ping_result, indent=2)}\n")
        
        # Example 3: List available tools
        print("3. Listing available tools...")
        tools_result = mcp_agent.execute_task({"operation": "list_tools"})
        print(f"Tools result: {json.dumps(tools_result, indent=2)}\n")
        
        # Example 4: Execute a secure command (if available)
        print("4. Executing secure command...")
        command_result = mcp_agent.execute_secure_command("ls -la")
        print(f"Command result: {json.dumps(command_result, indent=2)}\n")
        
        # Example 5: List resources
        print("5. Listing available resources...")
        resources_result = mcp_agent.execute_task({"operation": "list_resources"})
        print(f"Resources result: {json.dumps(resources_result, indent=2)}\n")
        
        # Example 6: List prompts
        print("6. Listing available prompts...")
        prompts_result = mcp_agent.execute_task({"operation": "list_prompts"})
        print(f"Prompts result: {json.dumps(prompts_result, indent=2)}\n")
        
        # Example 7: WebSocket communication example
        print("7. Testing WebSocket communication...")
        ws_result = mcp_agent.execute_task({"operation": "websocket_example"})
        print(f"WebSocket result: {json.dumps(ws_result, indent=2)}\n")
        
        # Show agent information
        agent_info = mcp_agent.get_agent_info()
        print(f"Agent info: {json.dumps(agent_info, indent=2)}")
        
        print("\n=== MCP Agent Examples Completed ===")
        
    except Exception as e:
        logger.error(f"Example execution failed: {e}")
        print(f"Error: {e}")
        return 1
    
    return 0


def run_with_python_agent_main():
    """
    Example of how to run the MCP agent using the main.py interface
    """
    print("\n=== Running via python-agent main.py ===")
    print("You can also run the MCP agent directly using:")
    print(f"cd {python_agent_dir}")
    print("python main.py mcp --task-data '{\"operation\": \"ping\"}'")
    print("python main.py mcp --task-data '{\"operation\": \"list_tools\"}'")
    print("python main.py mcp --task-data '{\"operation\": \"call_tool\", \"tool_name\": \"secure_command\", \"arguments\": {\"command\": \"ls -la\"}}'")


if __name__ == "__main__":
    exit_code = main()
    run_with_python_agent_main()
    sys.exit(exit_code)