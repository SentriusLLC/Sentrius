#!/usr/bin/env python3
"""
Coding MCP Server - Model Context Protocol server for code generation and PR submission.

This server exposes the coding agent functionality through the MCP protocol,
allowing enterprise agents to discover and use coding capabilities.
"""

import os
import sys
import json
import logging
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Dict, Any, List
import traceback

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from utils.config_manager import ConfigManager
from agents.coding.coding_agent import CodingAgent

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class MCPServer:
    """
    MCP Server for Coding Agent
    Implements the Model Context Protocol for code generation and PR submission
    """
    
    def __init__(self):
        self.config_manager = ConfigManager('application.properties')
        self.coding_agent = CodingAgent(self.config_manager)
        
        # Define MCP tools
        self.tools = self._define_tools()
        
        logger.info("Coding MCP Server initialized")
    
    def _define_tools(self) -> List[Dict[str, Any]]:
        """Define the tools exposed by this MCP server"""
        return [
            {
                "name": "handleJiraIssue",
                "description": "Generate code and create a pull request for a JIRA issue",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "issueKey": {
                            "type": "string",
                            "description": "JIRA issue key (e.g., PROJECT-123)"
                        },
                        "repository": {
                            "type": "string",
                            "description": "GitHub repository (format: owner/repo)"
                        },
                        "context": {
                            "type": "object",
                            "description": "Additional context for code generation (language, framework, etc.)",
                            "properties": {
                                "language": {"type": "string"},
                                "framework": {"type": "string"}
                            }
                        }
                    },
                    "required": ["issueKey", "repository"]
                }
            },
            {
                "name": "handleGitHubIssue",
                "description": "Generate code and create a pull request for a GitHub issue",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "repository": {
                            "type": "string",
                            "description": "GitHub repository (format: owner/repo)"
                        },
                        "issueNumber": {
                            "type": "integer",
                            "description": "GitHub issue number"
                        },
                        "context": {
                            "type": "object",
                            "description": "Additional context for code generation",
                            "properties": {
                                "language": {"type": "string"},
                                "framework": {"type": "string"}
                            }
                        }
                    },
                    "required": ["repository", "issueNumber"]
                }
            },
            {
                "name": "createPullRequest",
                "description": "Create a pull request with specified code changes",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "repository": {
                            "type": "string",
                            "description": "GitHub repository (format: owner/repo)"
                        },
                        "title": {
                            "type": "string",
                            "description": "Pull request title"
                        },
                        "description": {
                            "type": "string",
                            "description": "Pull request description"
                        },
                        "codeChanges": {
                            "type": "object",
                            "description": "Code changes to include in the PR"
                        }
                    },
                    "required": ["repository", "title", "description"]
                }
            }
        ]
    
    def handle_initialize(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """Handle MCP initialize request"""
        return {
            "protocolVersion": "1.0.0",
            "capabilities": {
                "tools": {}
            },
            "serverInfo": {
                "name": "coding-mcp-server",
                "version": "1.0.0"
            }
        }
    
    def handle_tools_list(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """Handle tools/list request"""
        return {
            "tools": self.tools
        }
    
    def handle_tools_call(self, params: Dict[str, Any]) -> Dict[str, Any]:
        """Handle tools/call request"""
        tool_name = params.get("name")
        arguments = params.get("arguments", {})
        
        logger.info(f"Calling tool: {tool_name} with arguments: {arguments}")
        
        try:
            if tool_name == "handleJiraIssue":
                task_data = {
                    "operation": "handle_jira_issue",
                    "issue_key": arguments["issueKey"],
                    "repo": arguments["repository"],
                    "context": arguments.get("context", {})
                }
                result = self.coding_agent.execute_task(task_data)
                return {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(result, indent=2)
                        }
                    ]
                }
            
            elif tool_name == "handleGitHubIssue":
                task_data = {
                    "operation": "handle_github_issue",
                    "repo": arguments["repository"],
                    "issue_number": arguments["issueNumber"],
                    "context": arguments.get("context", {})
                }
                result = self.coding_agent.execute_task(task_data)
                return {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(result, indent=2)
                        }
                    ]
                }
            
            elif tool_name == "createPullRequest":
                task_data = {
                    "operation": "create_pr",
                    "repo": arguments["repository"],
                    "title": arguments["title"],
                    "description": arguments["description"],
                    "code_changes": arguments.get("codeChanges")
                }
                result = self.coding_agent.execute_task(task_data)
                return {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(result, indent=2)
                        }
                    ]
                }
            
            else:
                raise ValueError(f"Unknown tool: {tool_name}")
        
        except Exception as e:
            logger.error(f"Error executing tool {tool_name}: {e}")
            logger.error(traceback.format_exc())
            return {
                "content": [
                    {
                        "type": "text",
                        "text": f"Error: {str(e)}"
                    }
                ],
                "isError": True
            }
    
    def handle_request(self, request_data: Dict[str, Any]) -> Dict[str, Any]:
        """Handle incoming MCP request"""
        method = request_data.get("method")
        params = request_data.get("params", {})
        request_id = request_data.get("id")
        
        logger.info(f"Handling MCP request: {method}")
        
        try:
            if method == "initialize":
                result = self.handle_initialize(params)
            elif method == "tools/list":
                result = self.handle_tools_list(params)
            elif method == "tools/call":
                result = self.handle_tools_call(params)
            else:
                raise ValueError(f"Unknown method: {method}")
            
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "result": result
            }
        
        except Exception as e:
            logger.error(f"Error handling request: {e}")
            logger.error(traceback.format_exc())
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "error": {
                    "code": -32603,
                    "message": str(e)
                }
            }


class MCPHTTPHandler(BaseHTTPRequestHandler):
    """HTTP handler for MCP requests"""
    
    mcp_server = None  # Will be set by the main function
    
    def do_POST(self):
        """Handle POST requests"""
        if self.path == "/mcp":
            try:
                content_length = int(self.headers['Content-Length'])
                post_data = self.rfile.read(content_length)
                request_data = json.loads(post_data.decode('utf-8'))
                
                # Handle the MCP request
                response_data = self.mcp_server.handle_request(request_data)
                
                # Send response
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response_data).encode('utf-8'))
            
            except Exception as e:
                logger.error(f"Error handling HTTP request: {e}")
                logger.error(traceback.format_exc())
                self.send_response(500)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                error_response = {
                    "jsonrpc": "2.0",
                    "error": {
                        "code": -32603,
                        "message": str(e)
                    }
                }
                self.wfile.write(json.dumps(error_response).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_GET(self):
        """Handle GET requests"""
        if self.path == "/health":
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "healthy"}).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        """Override to use logger"""
        logger.info(f"{self.address_string()} - {format % args}")


def main():
    """Main entry point"""
    # Get configuration from environment
    port = int(os.getenv('MCP_SERVER_PORT', '3000'))
    mode = os.getenv('MCP_SERVER_MODE', 'http')
    
    logger.info(f"Starting Coding MCP Server in {mode} mode on port {port}")
    
    # Initialize MCP server
    mcp_server = MCPServer()
    MCPHTTPHandler.mcp_server = mcp_server
    
    if mode == 'http':
        # Run HTTP server
        server = HTTPServer(('0.0.0.0', port), MCPHTTPHandler)
        logger.info(f"HTTP MCP Server listening on port {port}")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            logger.info("Shutting down server...")
            server.shutdown()
    else:
        # Stdio mode (for direct MCP protocol communication)
        logger.info("Running in stdio mode")
        while True:
            try:
                line = sys.stdin.readline()
                if not line:
                    break
                
                request_data = json.loads(line)
                response_data = mcp_server.handle_request(request_data)
                sys.stdout.write(json.dumps(response_data) + '\n')
                sys.stdout.flush()
            except Exception as e:
                logger.error(f"Error in stdio mode: {e}")
                break


if __name__ == "__main__":
    main()
