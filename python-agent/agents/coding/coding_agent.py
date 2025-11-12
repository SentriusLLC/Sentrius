"""
Coding Agent - Automates code generation and PR submission via GitHub/JIRA integration.

This agent can:
- Accept coding tasks from JIRA issues or GitHub issues
- Generate code using LLM integration
- Create pull requests with the generated code
- Update JIRA issues with PR links
- Track provenance for all operations
"""
import logging
import json
import requests
from typing import Dict, Any, Optional, List
from agents.base import BaseAgent

logger = logging.getLogger(__name__)


class CodingAgent(BaseAgent):
    """Agent that handles automated coding tasks and PR submissions."""
    
    def __init__(self, config_manager):
        super().__init__(config_manager, name="coding-agent")
        self.agent_definition = config_manager.get_agent_definition('coding')
        if not self.agent_definition:
            raise ValueError("Coding agent configuration not found")
        
        # Initialize services if not in test mode
        if not self.test_mode:
            # Get API URLs from configuration
            self.integration_proxy_url = self.agent_definition.get(
                'integration_proxy_url', 
                'http://localhost:8080'
            ).rstrip('/')
            self.llm_proxy_url = self.agent_definition.get(
                'llm_proxy_url',
                'http://localhost:8080'
            ).rstrip('/')
            
            # Create session with authentication
            self.session = requests.Session()
            self._update_auth_headers()
        else:
            self.integration_proxy_url = None
            self.llm_proxy_url = None
            self.session = None
        
        logger.info(f"Initialized CodingAgent: {self.agent_definition.get('description', 'No description')}")
    
    def _update_auth_headers(self):
        """Update session headers with current JWT token."""
        if self.sentrius_agent and self.session:
            try:
                token = self.sentrius_agent.keycloak_service.get_keycloak_token()
                self.session.headers.update({
                    'Authorization': f'Bearer {token}',
                    'Content-Type': 'application/json'
                })
            except Exception as e:
                logger.error(f"Failed to update auth headers: {e}")
                raise
    
    def execute_task(self, task_data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """
        Execute a coding task.
        
        Expected task_data format:
        {
            "operation": "create_pr" | "handle_jira_issue" | "handle_github_issue",
            "source": "jira" | "github",
            "issue_key": "PROJECT-123",  # For JIRA
            "repo": "owner/repo",         # For GitHub
            "issue_number": 123,          # For GitHub
            "title": "PR title",
            "description": "Task description",
            "code_changes": {...}         # Optional pre-generated changes
        }
        """
        try:
            self.submit_provenance("CODING_TASK_START", {
                "agent_type": "coding",
                "task_data": task_data
            })
            
            if self.test_mode:
                logger.info("Coding Agent running in test mode")
                return {
                    "status": "test_mode",
                    "message": "Coding operations would be executed here",
                    "task_data": task_data
                }
            
            # Validate task data
            if not task_data:
                raise ValueError("Task data is required")
            
            operation = task_data.get('operation', 'create_pr')
            
            # Route to appropriate handler
            if operation == 'handle_jira_issue':
                result = self._handle_jira_issue(task_data)
            elif operation == 'handle_github_issue':
                result = self._handle_github_issue(task_data)
            elif operation == 'create_pr':
                result = self._create_pull_request(task_data)
            else:
                raise ValueError(f"Unknown operation: {operation}")
            
            self.submit_provenance("CODING_TASK_COMPLETE", {
                "agent_type": "coding",
                "operation": operation,
                "result": result
            })
            
            return result
            
        except Exception as e:
            logger.error(f"Error executing coding task: {e}")
            self.submit_provenance("CODING_TASK_ERROR", {
                "agent_type": "coding",
                "error": str(e)
            })
            raise
    
    def _handle_jira_issue(self, task_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Handle a coding task from a JIRA issue.
        
        1. Fetch issue details from JIRA
        2. Generate code using LLM
        3. Create PR on GitHub
        4. Update JIRA issue with PR link
        """
        issue_key = task_data.get('issue_key')
        if not issue_key:
            raise ValueError("issue_key is required for JIRA issues")
        
        logger.info(f"Handling JIRA issue: {issue_key}")
        
        # Fetch JIRA issue details
        jira_issue = self._get_jira_issue(issue_key)
        
        # Extract task requirements
        description = jira_issue.get('fields', {}).get('description', '')
        summary = jira_issue.get('fields', {}).get('summary', '')
        
        # Generate code using LLM
        code_changes = self._generate_code_with_llm(
            title=summary,
            description=description,
            context=task_data.get('context', {})
        )
        
        # Create PR with generated code
        pr_data = {
            'repo': task_data.get('repo'),
            'title': f"{issue_key}: {summary}",
            'description': f"Automated PR for JIRA issue {issue_key}\n\n{description}",
            'code_changes': code_changes
        }
        pr_result = self._create_pull_request(pr_data)
        
        # Update JIRA issue with PR link
        pr_url = pr_result.get('pr_url')
        if pr_url:
            self._add_jira_comment(
                issue_key,
                f"Pull request created: {pr_url}"
            )
        
        return {
            "status": "success",
            "operation": "handle_jira_issue",
            "issue_key": issue_key,
            "pr_url": pr_url,
            "message": f"Successfully created PR for JIRA issue {issue_key}"
        }
    
    def _handle_github_issue(self, task_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Handle a coding task from a GitHub issue.
        
        1. Fetch issue details from GitHub
        2. Generate code using LLM
        3. Create PR on GitHub
        """
        repo = task_data.get('repo')
        issue_number = task_data.get('issue_number')
        
        if not repo or not issue_number:
            raise ValueError("repo and issue_number are required for GitHub issues")
        
        logger.info(f"Handling GitHub issue: {repo}#{issue_number}")
        
        # Fetch GitHub issue details
        github_issue = self._get_github_issue(repo, issue_number)
        
        # Extract task requirements
        title = github_issue.get('title', '')
        description = github_issue.get('body', '')
        
        # Generate code using LLM
        code_changes = self._generate_code_with_llm(
            title=title,
            description=description,
            context=task_data.get('context', {})
        )
        
        # Create PR with generated code
        pr_data = {
            'repo': repo,
            'title': f"Fix #{issue_number}: {title}",
            'description': f"Automated PR for issue #{issue_number}\n\n{description}",
            'code_changes': code_changes,
            'issue_number': issue_number
        }
        pr_result = self._create_pull_request(pr_data)
        
        return {
            "status": "success",
            "operation": "handle_github_issue",
            "repo": repo,
            "issue_number": issue_number,
            "pr_url": pr_result.get('pr_url'),
            "message": f"Successfully created PR for GitHub issue #{issue_number}"
        }
    
    def _create_pull_request(self, pr_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Create a pull request on GitHub using the GitHub MCP server.
        
        Steps:
        1. Launch GitHub MCP server (if not already running)
        2. Create branch
        3. Commit changes
        4. Create PR
        """
        repo = pr_data.get('repo')
        title = pr_data.get('title')
        description = pr_data.get('description')
        code_changes = pr_data.get('code_changes', {})
        
        if not all([repo, title]):
            raise ValueError("repo and title are required for PR creation")
        
        logger.info(f"Creating pull request: {title} for {repo}")
        
        # Get GitHub token ID from configuration
        github_token_id = self.agent_definition.get('github_token_id')
        if not github_token_id:
            raise ValueError("github_token_id must be configured")
        
        # Ensure GitHub MCP server is running
        self._ensure_github_mcp_server(github_token_id)
        
        # Use GitHub MCP proxy to create PR
        mcp_operations = [
            # Create a new branch
            {
                "operation": "create_branch",
                "branch_name": f"automated/{self._sanitize_branch_name(title)}",
                "base_branch": "main"
            },
            # Commit changes
            {
                "operation": "commit_changes",
                "files": code_changes.get('files', []),
                "message": f"{title}\n\n{description}"
            },
            # Create PR
            {
                "operation": "create_pull_request",
                "title": title,
                "body": description,
                "head": f"automated/{self._sanitize_branch_name(title)}",
                "base": "main"
            }
        ]
        
        # Execute MCP operations via proxy
        results = []
        for operation in mcp_operations:
            result = self._call_github_mcp_tool(github_token_id, operation)
            results.append(result)
        
        # Extract PR URL from final result
        pr_url = results[-1].get('html_url') if results else None
        
        return {
            "status": "success",
            "pr_url": pr_url,
            "operations": results,
            "message": f"Pull request created: {pr_url}"
        }
    
    def _generate_code_with_llm(self, title: str, description: str, 
                                context: Dict[str, Any]) -> Dict[str, Any]:
        """
        Generate code using LLM integration.
        
        Returns:
        {
            "files": [
                {
                    "path": "path/to/file.py",
                    "content": "file content",
                    "operation": "create" | "update" | "delete"
                }
            ],
            "explanation": "Explanation of changes"
        }
        """
        logger.info(f"Generating code with LLM for: {title}")
        
        # Prepare LLM prompt
        prompt = self._build_coding_prompt(title, description, context)
        
        # Call LLM proxy
        self._update_auth_headers()
        url = f"{self.llm_proxy_url}/api/v1/llm/chat"
        
        request_data = {
            "messages": [
                {
                    "role": "system",
                    "content": self.agent_definition.get('context', '')
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            "model": self.agent_definition.get('llm_model', 'gpt-4'),
            "temperature": 0.7
        }
        
        try:
            response = self.session.post(url, json=request_data)
            response.raise_for_status()
            llm_response = response.json()
            
            # Parse LLM response to extract code changes
            code_content = llm_response.get('choices', [{}])[0].get('message', {}).get('content', '')
            code_changes = self._parse_llm_code_response(code_content)
            
            return code_changes
            
        except Exception as e:
            logger.error(f"Failed to generate code with LLM: {e}")
            raise
    
    def _build_coding_prompt(self, title: str, description: str, 
                            context: Dict[str, Any]) -> str:
        """Build a comprehensive prompt for code generation."""
        prompt = f"""
Task: {title}

Description:
{description}

Context:
{json.dumps(context, indent=2)}

Please generate the necessary code changes to complete this task.
Return your response in the following JSON format:
{{
    "files": [
        {{
            "path": "path/to/file",
            "content": "file content here",
            "operation": "create|update|delete"
        }}
    ],
    "explanation": "Brief explanation of changes"
}}
"""
        return prompt
    
    def _parse_llm_code_response(self, response_content: str) -> Dict[str, Any]:
        """Parse LLM response to extract structured code changes."""
        try:
            # Try to find JSON in the response
            start_idx = response_content.find('{')
            end_idx = response_content.rfind('}') + 1
            
            if start_idx >= 0 and end_idx > start_idx:
                json_str = response_content[start_idx:end_idx]
                return json.loads(json_str)
            else:
                # Fallback: return raw content
                return {
                    "files": [],
                    "explanation": response_content
                }
        except json.JSONDecodeError:
            logger.warning("Failed to parse LLM response as JSON")
            return {
                "files": [],
                "explanation": response_content
            }
    
    def _ensure_github_mcp_server(self, token_id: str) -> None:
        """Ensure GitHub MCP server is running for the given token."""
        logger.info(f"Ensuring GitHub MCP server for token: {token_id}")
        
        self._update_auth_headers()
        
        # Check status
        status_url = f"{self.integration_proxy_url}/api/v1/github/mcp/status"
        try:
            response = self.session.get(status_url, params={"tokenId": token_id})
            if response.status_code == 200:
                status = response.json().get('status')
                if status == 'Running':
                    logger.info("GitHub MCP server is already running")
                    return
        except Exception as e:
            logger.debug(f"Status check failed, will launch: {e}")
        
        # Launch server
        launch_url = f"{self.integration_proxy_url}/api/v1/github/mcp/launch"
        try:
            response = self.session.post(launch_url, params={"tokenId": token_id})
            response.raise_for_status()
            logger.info("GitHub MCP server launched successfully")
        except Exception as e:
            logger.error(f"Failed to launch GitHub MCP server: {e}")
            raise
    
    def _call_github_mcp_tool(self, token_id: str, operation: Dict[str, Any]) -> Dict[str, Any]:
        """Call a GitHub MCP tool via the proxy."""
        logger.info(f"Calling GitHub MCP tool: {operation.get('operation')}")
        
        self._update_auth_headers()
        url = f"{self.integration_proxy_url}/api/v1/github/mcp/proxy"
        
        mcp_request = {
            "method": "tools/call",
            "id": f"coding-agent-{operation.get('operation')}",
            "params": {
                "name": operation.get('operation'),
                "arguments": {k: v for k, v in operation.items() if k != 'operation'}
            }
        }
        
        try:
            response = self.session.post(
                url, 
                params={"tokenId": token_id},
                json=mcp_request
            )
            response.raise_for_status()
            return response.json()
        except Exception as e:
            logger.error(f"Failed to call GitHub MCP tool: {e}")
            raise
    
    def _get_jira_issue(self, issue_key: str) -> Dict[str, Any]:
        """Fetch JIRA issue details."""
        logger.info(f"Fetching JIRA issue: {issue_key}")
        
        self._update_auth_headers()
        url = f"{self.integration_proxy_url}/api/v1/jira/rest/api/3/issue/{issue_key}"
        
        try:
            response = self.session.get(url)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            logger.error(f"Failed to fetch JIRA issue: {e}")
            raise
    
    def _add_jira_comment(self, issue_key: str, comment: str) -> None:
        """Add a comment to a JIRA issue."""
        logger.info(f"Adding comment to JIRA issue: {issue_key}")
        
        self._update_auth_headers()
        url = f"{self.integration_proxy_url}/api/v1/jira/rest/api/3/issue/{issue_key}/comment"
        
        try:
            response = self.session.post(url, json={"text": comment})
            response.raise_for_status()
            logger.info("Comment added successfully")
        except Exception as e:
            logger.error(f"Failed to add JIRA comment: {e}")
            # Don't raise - comment failure shouldn't fail the whole operation
    
    def _get_github_issue(self, repo: str, issue_number: int) -> Dict[str, Any]:
        """Fetch GitHub issue details via MCP server."""
        logger.info(f"Fetching GitHub issue: {repo}#{issue_number}")
        
        github_token_id = self.agent_definition.get('github_token_id')
        if not github_token_id:
            raise ValueError("github_token_id must be configured")
        
        self._ensure_github_mcp_server(github_token_id)
        
        operation = {
            "operation": "get_issue",
            "owner": repo.split('/')[0],
            "repo": repo.split('/')[1],
            "issue_number": issue_number
        }
        
        return self._call_github_mcp_tool(github_token_id, operation)
    
    def _sanitize_branch_name(self, name: str) -> str:
        """Sanitize a string to be a valid Git branch name."""
        import re
        # Replace spaces and special characters with hyphens
        sanitized = re.sub(r'[^a-zA-Z0-9-_]', '-', name)
        # Remove consecutive hyphens
        sanitized = re.sub(r'-+', '-', sanitized)
        # Limit length
        return sanitized[:50].strip('-').lower()
    
    def get_agent_info(self) -> Dict[str, Any]:
        """Get information about this agent."""
        return {
            "name": "coding",
            "type": "automation",
            "description": self.agent_definition.get('description', ''),
            "capabilities": [
                "jira_issue_handling",
                "github_issue_handling",
                "code_generation",
                "pull_request_creation",
                "automated_coding"
            ]
        }
