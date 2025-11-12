"""
Example Python script demonstrating automated coding workflows with the Coding Agent.
This can be integrated with CI/CD pipelines, cron jobs, or other automation tools.
"""

import os
import sys
import json
import subprocess
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class CodingAgentAutomation:
    """Automation wrapper for the Coding Agent."""
    
    def __init__(self, agent_path="/path/to/sentrius/python-agent"):
        self.agent_path = agent_path
        self.main_script = os.path.join(agent_path, "main.py")
    
    def run_coding_task(self, task_data):
        """
        Execute a coding task using the coding agent.
        
        Args:
            task_data (dict): Task data for the coding agent
            
        Returns:
            dict: Result from the coding agent
        """
        logger.info(f"Running coding task: {task_data.get('operation')}")
        
        # Prepare command
        cmd = [
            "python3",
            self.main_script,
            "coding",
            "--task-data",
            json.dumps(task_data)
        ]
        
        # Execute
        try:
            result = subprocess.run(
                cmd,
                cwd=self.agent_path,
                capture_output=True,
                text=True,
                check=True
            )
            logger.info("Coding task completed successfully")
            return {
                "status": "success",
                "output": result.stdout
            }
        except subprocess.CalledProcessError as e:
            logger.error(f"Coding task failed: {e.stderr}")
            return {
                "status": "error",
                "error": e.stderr
            }
    
    def handle_jira_issue(self, issue_key, repo, context=None):
        """
        Handle a JIRA issue with automated code generation.
        
        Args:
            issue_key (str): JIRA issue key (e.g., "PROJECT-123")
            repo (str): GitHub repository (e.g., "owner/repo")
            context (dict): Additional context for code generation
            
        Returns:
            dict: Result from the coding agent
        """
        task_data = {
            "operation": "handle_jira_issue",
            "issue_key": issue_key,
            "repo": repo,
            "context": context or {}
        }
        return self.run_coding_task(task_data)
    
    def handle_github_issue(self, repo, issue_number, context=None):
        """
        Handle a GitHub issue with automated code generation.
        
        Args:
            repo (str): GitHub repository (e.g., "owner/repo")
            issue_number (int): GitHub issue number
            context (dict): Additional context for code generation
            
        Returns:
            dict: Result from the coding agent
        """
        task_data = {
            "operation": "handle_github_issue",
            "repo": repo,
            "issue_number": issue_number,
            "context": context or {}
        }
        return self.run_coding_task(task_data)
    
    def create_pr(self, repo, title, description, code_changes=None):
        """
        Create a pull request directly.
        
        Args:
            repo (str): GitHub repository (e.g., "owner/repo")
            title (str): PR title
            description (str): PR description
            code_changes (dict): Pre-generated code changes
            
        Returns:
            dict: Result from the coding agent
        """
        task_data = {
            "operation": "create_pr",
            "repo": repo,
            "title": title,
            "description": description
        }
        if code_changes:
            task_data["code_changes"] = code_changes
        
        return self.run_coding_task(task_data)


# Example usage
if __name__ == "__main__":
    automation = CodingAgentAutomation()
    
    # Example 1: Handle JIRA issue
    logger.info("Example 1: Handle JIRA issue")
    result = automation.handle_jira_issue(
        issue_key="PROJECT-123",
        repo="owner/repository",
        context={
            "language": "Python",
            "framework": "Flask"
        }
    )
    print(json.dumps(result, indent=2))
    
    # Example 2: Handle GitHub issue
    logger.info("Example 2: Handle GitHub issue")
    result = automation.handle_github_issue(
        repo="owner/repository",
        issue_number=456,
        context={
            "language": "Java",
            "framework": "Spring Boot"
        }
    )
    print(json.dumps(result, indent=2))
    
    # Example 3: Create PR directly
    logger.info("Example 3: Create PR directly")
    result = automation.create_pr(
        repo="owner/repository",
        title="Add new feature",
        description="Implementation of feature X",
        code_changes={
            "files": [
                {
                    "path": "src/main/java/Example.java",
                    "content": "public class Example { /* ... */ }",
                    "operation": "create"
                }
            ]
        }
    )
    print(json.dumps(result, indent=2))
