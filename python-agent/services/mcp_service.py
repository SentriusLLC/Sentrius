"""
MCP (Model Context Protocol) Service for Sentrius Python Agent
Integrates with existing Sentrius authentication and provenance systems.
"""

import json
import asyncio
import websockets
import requests
from typing import Dict, Any, Optional
import logging

logger = logging.getLogger(__name__)


class MCPService:
    """
    Service for communicating with Sentrius MCP Proxy
    Integrates with existing Sentrius authentication and provenance systems
    """
    
    def __init__(self, base_url: str, keycloak_service, agent_id: str):
        self.base_url = base_url.rstrip('/')
        self.keycloak_service = keycloak_service
        self.agent_id = agent_id
        self.session = requests.Session()
        self._update_auth_headers()
        
    def _update_auth_headers(self):
        """Update session headers with current JWT token"""
        try:
            token = self.keycloak_service.get_keycloak_token()
            self.session.headers.update({
                'Authorization': f'Bearer {token}',
                'Content-Type': 'application/json'
            })
        except Exception as e:
            logger.error(f"Failed to update auth headers: {e}")
            raise
        
    def send_mcp_request(self, method: str, params: Dict[str, Any] = None, 
                         communication_id: str = None) -> Dict[str, Any]:
        """
        Send MCP request via HTTP
        """
        if communication_id is None:
            communication_id = f"mcp-{method}-{self.agent_id}"
            
        request_data = {
            "jsonrpc": "2.0",
            "id": f"{method}-{self.agent_id}",
            "method": method,
            "params": params or {}
        }
        
        headers = dict(self.session.headers)
        headers['communication_id'] = communication_id
        
        url = f"{self.base_url}/api/v1/mcp/"
        
        try:
            # Refresh token if needed
            self._update_auth_headers()
            
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
        try:
            token = self.keycloak_service.get_keycloak_token()
            ws_url = self.base_url.replace('http://', 'ws://').replace('https://', 'wss://')
            ws_url += f"/api/v1/mcp/ws?token=Bearer%20{token}&communication_id=ws-{self.agent_id}&user_id={self.agent_id}"
            
            websocket = await websockets.connect(ws_url)
            return MCPWebSocketClient(websocket)
        except Exception as e:
            logger.error(f"Failed to connect WebSocket: {e}")
            raise
    
    def get_capabilities(self) -> Dict[str, Any]:
        """
        Get MCP proxy capabilities
        """
        url = f"{self.base_url}/api/v1/mcp/capabilities"
        try:
            self._update_auth_headers()
            response = self.session.get(url)
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            logger.error(f"Failed to get capabilities: {e}")
            raise
    
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