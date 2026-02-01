"""
Knowledge Graph Agent - Provides knowledge graph querying and investigation capabilities.
"""
import logging
import json
import requests
from typing import Dict, Any, Optional, List
from agents.base import BaseAgent

logger = logging.getLogger(__name__)


class KnowledgeGraphAgent(BaseAgent):
    """Agent that provides knowledge graph querying and investigation capabilities."""
    
    def __init__(self, config_manager):
        super().__init__(config_manager, name="knowledge-graph")
        self.agent_definition = config_manager.get_agent_definition('knowledge.graph')
        if not self.agent_definition:
            # Use default configuration if not found
            self.agent_definition = {
                'description': 'Knowledge graph query and investigation agent',
                'api_base_url': 'http://localhost:8080'
            }
        
        self.api_base_url = self.agent_definition.get('api_base_url', 'http://localhost:8080')
        logger.info(f"Initialized KnowledgeGraphAgent: {self.agent_definition.get('description')}")
    
    def execute_task(self, task_data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Execute knowledge graph task."""
        try:
            # Submit provenance for task start
            self.submit_provenance("KNOWLEDGE_GRAPH_TASK_START", {
                "agent_type": "knowledge_graph",
                "task_data": task_data
            })
            
            if self.test_mode:
                logger.info("Knowledge Graph Agent running in test mode")
                return {
                    "status": "test_mode",
                    "message": "Knowledge graph operations would be executed here"
                }
            
            # Process the knowledge graph request
            operation = task_data.get('operation', 'query') if task_data else 'query'
            response = self._route_operation(operation, task_data)
            
            # Submit provenance for task completion
            self.submit_provenance("KNOWLEDGE_GRAPH_TASK_COMPLETE", {
                "agent_type": "knowledge_graph",
                "operation": operation,
                "response": response
            })
            
            return response
            
        except Exception as e:
            logger.error(f"Error executing knowledge graph task: {e}")
            self.submit_provenance("KNOWLEDGE_GRAPH_TASK_ERROR", {
                "agent_type": "knowledge_graph",
                "error": str(e)
            })
            raise
    
    def _route_operation(self, operation: str, task_data: Dict[str, Any]) -> Dict[str, Any]:
        """Route operation to appropriate handler."""
        handlers = {
            'query': self._execute_query,
            'find_similar': self._find_similar_documents,
            'create_relationship': self._create_relationship,
            'get_neighbors': self._get_neighbors,
            'traverse': self._traverse_graph,
            'search': self._search_nodes,
        }
        
        handler = handlers.get(operation)
        if not handler:
            raise ValueError(f"Unknown operation: {operation}")
        
        return handler(task_data)
    
    def _execute_query(self, task_data: Dict[str, Any]) -> Dict[str, Any]:
        """Execute a knowledge graph query."""
        logger.info("Executing knowledge graph query")
        
        query_request = {
            'queryType': task_data.get('query_type', 'SEARCH'),
            'startNodeId': task_data.get('start_node_id'),
            'targetNodeId': task_data.get('target_node_id'),
            'searchText': task_data.get('search_text'),
            'nodeTypes': task_data.get('node_types'),
            'relationshipTypes': task_data.get('relationship_types'),
            'maxDepth': task_data.get('max_depth', 2),
            'limit': task_data.get('limit', 50),
            'customQuery': task_data.get('custom_query')
        }
        
        response = self._make_api_request(
            'POST',
            '/api/v1/knowledge-graph/query',
            json=query_request
        )
        
        return {
            'operation': 'query',
            'status': 'success',
            'data': response
        }
    
    def _find_similar_documents(self, task_data: Dict[str, Any]) -> Dict[str, Any]:
        """Find similar documents using the knowledge graph."""
        logger.info("Finding similar documents in knowledge graph")
        
        document_id = task_data.get('document_id')
        if not document_id:
            raise ValueError("document_id is required for find_similar operation")
        
        limit = task_data.get('limit', 10)
        
        response = self._make_api_request(
            'GET',
            f'/api/v1/knowledge-graph/documents/{document_id}/similar',
            params={'limit': limit}
        )
        
        return {
            'operation': 'find_similar',
            'document_id': document_id,
            'status': 'success',
            'data': response
        }
    
    def _create_relationship(self, task_data: Dict[str, Any]) -> Dict[str, Any]:
        """Create a relationship between two nodes."""
        logger.info("Creating knowledge graph relationship")
        
        from_node_id = task_data.get('from_node_id')
        to_node_id = task_data.get('to_node_id')
        relationship_type = task_data.get('relationship_type')
        
        if not all([from_node_id, to_node_id, relationship_type]):
            raise ValueError("from_node_id, to_node_id, and relationship_type are required")
        
        params = {
            'fromNodeId': from_node_id,
            'toNodeId': to_node_id,
            'relationshipType': relationship_type,
            'weight': task_data.get('weight')
        }
        
        response = self._make_api_request(
            'POST',
            '/api/v1/knowledge-graph/relationships',
            params=params
        )
        
        return {
            'operation': 'create_relationship',
            'status': 'success',
            'data': response
        }
    
    def _get_neighbors(self, task_data: Dict[str, Any]) -> Dict[str, Any]:
        """Get immediate neighbors of a node."""
        logger.info("Getting node neighbors from knowledge graph")
        
        query_request = {
            'queryType': 'NEIGHBORS',
            'startNodeId': task_data.get('node_id'),
            'limit': task_data.get('limit', 20)
        }
        
        response = self._make_api_request(
            'POST',
            '/api/v1/knowledge-graph/query',
            json=query_request
        )
        
        return {
            'operation': 'get_neighbors',
            'status': 'success',
            'data': response
        }
    
    def _traverse_graph(self, task_data: Dict[str, Any]) -> Dict[str, Any]:
        """Traverse the graph from a starting node."""
        logger.info("Traversing knowledge graph")
        
        query_request = {
            'queryType': 'TRAVERSE',
            'startNodeId': task_data.get('start_node_id'),
            'maxDepth': task_data.get('max_depth', 2),
            'relationshipTypes': task_data.get('relationship_types'),
            'limit': task_data.get('limit', 50)
        }
        
        response = self._make_api_request(
            'POST',
            '/api/v1/knowledge-graph/query',
            json=query_request
        )
        
        return {
            'operation': 'traverse',
            'status': 'success',
            'data': response
        }
    
    def _search_nodes(self, task_data: Dict[str, Any]) -> Dict[str, Any]:
        """Search for nodes by text."""
        logger.info("Searching knowledge graph nodes")
        
        query_request = {
            'queryType': 'SEARCH',
            'searchText': task_data.get('search_text', ''),
            'nodeTypes': task_data.get('node_types'),
            'limit': task_data.get('limit', 50)
        }
        
        response = self._make_api_request(
            'POST',
            '/api/v1/knowledge-graph/query',
            json=query_request
        )
        
        return {
            'operation': 'search',
            'status': 'success',
            'data': response
        }
    
    def _make_api_request(self, method: str, endpoint: str, **kwargs) -> Dict[str, Any]:
        """Make an authenticated API request to Sentrius."""
        if self.test_mode:
            return {'test_mode': True}
        
        url = f"{self.api_base_url}{endpoint}"
        
        # Get authentication token
        token = self.sentrius_agent.keycloak_service.get_token()
        headers = {
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json'
        }
        
        # Make request
        try:
            if method == 'GET':
                response = requests.get(url, headers=headers, **kwargs)
            elif method == 'POST':
                response = requests.post(url, headers=headers, **kwargs)
            elif method == 'DELETE':
                response = requests.delete(url, headers=headers, **kwargs)
            else:
                raise ValueError(f"Unsupported HTTP method: {method}")
            
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            logger.error(f"API request failed: {e}")
            raise
