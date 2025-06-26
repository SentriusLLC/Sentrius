"""
MCP Agent - Provides Model Context Protocol integration with Sentrius security.
"""
import logging
import asyncio
from typing import Dict, Any, Optional
from agents.base import BaseAgent
from services.mcp_service import MCPService

logger = logging.getLogger(__name__)


class MCPAgent(BaseAgent):
    """Agent that provides MCP (Model Context Protocol) integration with Sentrius security."""
    
    def __init__(self, config_manager):
        super().__init__(config_manager)
        self.agent_definition = config_manager.get_agent_definition('mcp')
        if not self.agent_definition:
            raise ValueError("MCP agent configuration not found")
        
        # Initialize MCP service with Sentrius integration
        if not self.test_mode:
            mcp_base_url = self.agent_definition.get('mcp_base_url', 'http://localhost:8080')
            self.mcp_service = MCPService(
                base_url=mcp_base_url,
                keycloak_service=self.sentrius_agent.keycloak_service,
                agent_id=self.sentrius_agent.agent_id
            )
        else:
            self.mcp_service = None
        
        logger.info(f"Initialized MCPAgent: {self.agent_definition.get('description', 'No description')}")
    
    def execute_task(self, task_data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Execute MCP task."""
        try:
            # Submit provenance for task start
            self.submit_provenance("MCP_TASK_START", {
                "agent_type": "mcp",
                "task_data": task_data
            })
            
            if self.test_mode:
                logger.info("MCP Agent running in test mode")
                return {
                    "status": "test_mode",
                    "message": "MCP operations would be executed here"
                }
            
            # Process the MCP request
            response = self._process_mcp_request(task_data)
            
            # Submit provenance for task completion
            self.submit_provenance("MCP_TASK_COMPLETE", {
                "agent_type": "mcp",
                "response": response
            })
            
            return response
            
        except Exception as e:
            logger.error(f"Error executing MCP task: {e}")
            self.submit_provenance("MCP_TASK_ERROR", {
                "agent_type": "mcp",
                "error": str(e)
            })
            raise
    
    def _process_mcp_request(self, task_data: Optional[Dict[str, Any]]) -> Dict[str, Any]:
        """Process MCP request using the integrated service."""
        if not task_data:
            # Default initialization
            try:
                # Initialize MCP session
                init_response = self.mcp_service.initialize()
                capabilities = self.mcp_service.get_capabilities()
                
                return {
                    "operation": "initialize",
                    "status": "success",
                    "init_response": init_response,
                    "capabilities": capabilities,
                    "message": "MCP agent initialized successfully"
                }
            except Exception as e:
                return {
                    "operation": "initialize",
                    "status": "error",
                    "error": str(e),
                    "message": "Failed to initialize MCP agent"
                }
        
        operation = task_data.get('operation', 'ping')
        
        try:
            if operation == 'ping':
                response = self.mcp_service.ping()
                return {
                    "operation": "ping",
                    "status": "success",
                    "response": response
                }
            
            elif operation == 'list_tools':
                response = self.mcp_service.list_tools()
                return {
                    "operation": "list_tools",
                    "status": "success",
                    "tools": response
                }
            
            elif operation == 'call_tool':
                tool_name = task_data.get('tool_name')
                arguments = task_data.get('arguments', {})
                if not tool_name:
                    raise ValueError("tool_name is required for call_tool operation")
                
                response = self.mcp_service.call_tool(tool_name, arguments)
                return {
                    "operation": "call_tool",
                    "status": "success",
                    "tool_name": tool_name,
                    "response": response
                }
            
            elif operation == 'list_resources':
                response = self.mcp_service.list_resources()
                return {
                    "operation": "list_resources",
                    "status": "success",
                    "resources": response
                }
            
            elif operation == 'read_resource':
                uri = task_data.get('uri')
                if not uri:
                    raise ValueError("uri is required for read_resource operation")
                
                response = self.mcp_service.read_resource(uri)
                return {
                    "operation": "read_resource",
                    "status": "success",
                    "uri": uri,
                    "response": response
                }
            
            elif operation == 'list_prompts':
                response = self.mcp_service.list_prompts()
                return {
                    "operation": "list_prompts",
                    "status": "success",
                    "prompts": response
                }
            
            elif operation == 'get_prompt':
                name = task_data.get('name')
                arguments = task_data.get('arguments', {})
                if not name:
                    raise ValueError("name is required for get_prompt operation")
                
                response = self.mcp_service.get_prompt(name, arguments)
                return {
                    "operation": "get_prompt",
                    "status": "success",
                    "name": name,
                    "response": response
                }
            
            elif operation == 'websocket_example':
                # Demonstrate WebSocket usage
                return asyncio.run(self._websocket_example())
            
            else:
                raise ValueError(f"Unknown operation: {operation}")
                
        except Exception as e:
            logger.error(f"MCP operation '{operation}' failed: {e}")
            return {
                "operation": operation,
                "status": "error",
                "error": str(e),
                "message": f"Failed to execute {operation}"
            }
    
    async def _websocket_example(self) -> Dict[str, Any]:
        """Example of WebSocket MCP communication."""
        try:
            ws_client = await self.mcp_service.connect_websocket()
            
            try:
                # Send ping via WebSocket
                ping_response = await ws_client.send_request("ping")
                
                # List tools via WebSocket
                tools_response = await ws_client.send_request("tools/list")
                
                return {
                    "operation": "websocket_example",
                    "status": "success",
                    "ping_response": ping_response,
                    "tools_response": tools_response
                }
                
            finally:
                await ws_client.close()
                
        except Exception as e:
            logger.error(f"WebSocket example failed: {e}")
            return {
                "operation": "websocket_example",
                "status": "error",
                "error": str(e)
            }
    
    def execute_secure_command(self, command: str) -> Dict[str, Any]:
        """
        Execute a secure command using MCP tools
        """
        try:
            task_data = {
                "operation": "call_tool",
                "tool_name": "secure_command",
                "arguments": {"command": command}
            }
            return self._process_mcp_request(task_data)
            
        except Exception as e:
            logger.error(f"Secure command execution failed: {e}")
            return {
                "operation": "call_tool",
                "status": "error",
                "error": str(e),
                "command": command
            }
    
    def get_agent_info(self) -> Dict[str, Any]:
        """Get information about this agent."""
        return {
            "name": "mcp",
            "type": "protocol_integration",
            "description": self.agent_definition.get('description', ''),
            "capabilities": [
                "mcp_protocol",
                "secure_tool_execution",
                "resource_access",
                "prompt_management",
                "websocket_communication"
            ]
        }