#!/usr/bin/env python3
"""
Example MCP client integration with Sentrius MCP Proxy

This example shows how to connect to the Sentrius MCP proxy and use it
with the existing Python agent framework.
"""

import json
import asyncio
import websockets
import requests
from typing import Dict, Any, Optional
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class SentriusMCPClient:
    """
    Client for communicating with Sentrius MCP Proxy
    """
    
    def __init__(self, base_url: str, jwt_token: str, user_id: str):
        self.base_url = base_url.rstrip('/')
        self.jwt_token = jwt_token
        self.user_id = user_id
        self.session = requests.Session()
        self.session.headers.update({
            'Authorization': f'Bearer {jwt_token}',
            'Content-Type': 'application/json'
        })
        
    def send_mcp_request(self, method: str, params: Dict[str, Any] = None, 
                         communication_id: str = None) -> Dict[str, Any]:
        """
        Send MCP request via HTTP
        """
        if communication_id is None:
            communication_id = f"mcp-{method}-{id(self)}"
            
        request_data = {
            "jsonrpc": "2.0",
            "id": f"{method}-{id(self)}",
            "method": method,
            "params": params or {}
        }
        
        headers = dict(self.session.headers)
        headers['communication_id'] = communication_id
        
        url = f"{self.base_url}/api/v1/mcp/"
        
        try:
            response = self.session.post(url, json=request_data, headers=headers)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            logger.error(f"MCP request failed: {e}")
            raise
    
    async def connect_websocket(self) -> 'MCPWebSocketClient':
        """
        Create WebSocket connection for real-time MCP communication
        """
        ws_url = self.base_url.replace('http://', 'ws://').replace('https://', 'wss://')
        ws_url += f"/api/v1/mcp/ws?token=Bearer%20{self.jwt_token}&communication_id=ws-{id(self)}&user_id={self.user_id}"
        
        websocket = await websockets.connect(ws_url)
        return MCPWebSocketClient(websocket)
    
    def get_capabilities(self) -> Dict[str, Any]:
        """
        Get MCP proxy capabilities
        """
        url = f"{self.base_url}/api/v1/mcp/capabilities"
        response = self.session.get(url)
        response.raise_for_status()
        return response.json()
    
    def initialize(self) -> Dict[str, Any]:
        """
        Initialize MCP session
        """
        return self.send_mcp_request("initialize")
    
    def ping(self) -> Dict[str, Any]:
        """
        Send ping for connectivity check
        """
        return self.send_mcp_request("ping")
    
    def list_tools(self) -> Dict[str, Any]:
        """
        List available tools
        """
        return self.send_mcp_request("tools/list")
    
    def call_tool(self, tool_name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        """
        Execute a tool
        """
        params = {
            "name": tool_name,
            "arguments": arguments
        }
        return self.send_mcp_request("tools/call", params)
    
    def list_resources(self) -> Dict[str, Any]:
        """
        List available resources
        """
        return self.send_mcp_request("resources/list")
    
    def read_resource(self, uri: str) -> Dict[str, Any]:
        """
        Read a specific resource
        """
        params = {"uri": uri}
        return self.send_mcp_request("resources/read", params)
    
    def list_prompts(self) -> Dict[str, Any]:
        """
        List available prompts
        """
        return self.send_mcp_request("prompts/list")
    
    def get_prompt(self, name: str, arguments: Dict[str, Any] = None) -> Dict[str, Any]:
        """
        Get a specific prompt
        """
        params = {"name": name}
        if arguments:
            params["arguments"] = arguments
        return self.send_mcp_request("prompts/get", params)


class MCPWebSocketClient:
    """
    WebSocket client for real-time MCP communication
    """
    
    def __init__(self, websocket):
        self.websocket = websocket
        self.request_id_counter = 0
    
    async def send_request(self, method: str, params: Dict[str, Any] = None) -> Dict[str, Any]:
        """
        Send MCP request via WebSocket
        """
        self.request_id_counter += 1
        request_id = f"{method}-{self.request_id_counter}"
        
        request_data = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
            "params": params or {}
        }
        
        await self.websocket.send(json.dumps(request_data))
        
        # Wait for response
        response_text = await self.websocket.recv()
        return json.loads(response_text)
    
    async def close(self):
        """
        Close WebSocket connection
        """
        await self.websocket.close()


# Example usage with Python agents
class SentriusMCPAgent:
    """
    Example agent that uses Sentrius MCP proxy
    """
    
    def __init__(self, base_url: str, jwt_token: str, user_id: str):
        self.mcp_client = SentriusMCPClient(base_url, jwt_token, user_id)
        
    def execute_secure_command(self, command: str) -> str:
        """
        Execute a secure command using MCP tools
        """
        try:
            # Initialize if needed
            init_response = self.mcp_client.initialize()
            logger.info(f"MCP initialized: {init_response}")
            
            # List available tools
            tools_response = self.mcp_client.list_tools()
            logger.info(f"Available tools: {tools_response}")
            
            # Execute the secure command tool
            result = self.mcp_client.call_tool("secure_command", {"command": command})
            
            if result.get("error"):
                raise Exception(f"Tool execution failed: {result['error']}")
                
            return result.get("result", {}).get("content", "No result")
            
        except Exception as e:
            logger.error(f"MCP command execution failed: {e}")
            raise
    
    async def real_time_interaction(self):
        """
        Example of real-time MCP interaction via WebSocket
        """
        ws_client = await self.mcp_client.connect_websocket()
        
        try:
            # Send ping
            ping_response = await ws_client.send_request("ping")
            logger.info(f"Ping response: {ping_response}")
            
            # List tools
            tools_response = await ws_client.send_request("tools/list")
            logger.info(f"Tools: {tools_response}")
            
        finally:
            await ws_client.close()


def main():
    """
    Example usage
    """
    # Configuration - replace with actual values
    BASE_URL = "http://localhost:8080"
    JWT_TOKEN = "your-jwt-token-here"
    USER_ID = "your-user-id"
    
    # Create agent
    agent = SentriusMCPAgent(BASE_URL, JWT_TOKEN, USER_ID)
    
    try:
        # Test HTTP endpoint
        print("Testing HTTP MCP communication...")
        
        # Get capabilities
        capabilities = agent.mcp_client.get_capabilities()
        print(f"Capabilities: {json.dumps(capabilities, indent=2)}")
        
        # Test ping
        ping_result = agent.mcp_client.ping()
        print(f"Ping result: {json.dumps(ping_result, indent=2)}")
        
        # Execute a secure command
        command_result = agent.execute_secure_command("ls -la")
        print(f"Command result: {command_result}")
        
        # Test WebSocket communication
        print("\nTesting WebSocket MCP communication...")
        asyncio.run(agent.real_time_interaction())
        
    except Exception as e:
        logger.error(f"Example failed: {e}")


if __name__ == "__main__":
    main()