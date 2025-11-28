"""Tests for prompt_advisor service."""
import pytest
from fastapi.testclient import TestClient
from prompt_advisor.main import app
from prompt_advisor.llm_evaluator import LLMEvaluator

client = TestClient(app)


def test_root_endpoint():
    """Test root endpoint returns service information."""
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["service"] == "prompt_advisor"
    assert data["status"] == "running"
    assert "version" in data


def test_health_endpoint():
    """Test health check endpoint."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"


def test_criteria_endpoint():
    """Test criteria endpoint returns ATPL categories."""
    response = client.get("/criteria")
    assert response.status_code == 200
    data = response.json()
    
    assert "criteria" in data
    assert "total_weight" in data
    assert len(data["criteria"]) == 5
    assert data["total_weight"] == 100
    
    # Check that all expected categories are present
    category_names = [c["name"].lower() for c in data["criteria"]]
    assert "purpose" in category_names
    assert "safety" in category_names
    assert "compliance" in category_names
    assert "provenance" in category_names
    assert "autonomy" in category_names


def test_validate_prompt_basic():
    """Test basic prompt validation."""
    request_data = {
        "prompt": "Generate a summary of customer feedback"
    }
    
    response = client.post("/validate_prompt", json=request_data)
    assert response.status_code == 200
    data = response.json()
    
    # Check response structure
    assert "score" in data
    assert "ratings" in data
    assert "explanation" in data
    assert "recommendations" in data
    
    # Check score is in valid range
    assert 0 <= data["score"] <= 100
    
    # Check ratings structure
    ratings = data["ratings"]
    assert "purpose" in ratings
    assert "safety" in ratings
    assert "compliance" in ratings
    assert "provenance" in ratings
    assert "autonomy" in ratings
    
    # Check all ratings are in valid range (0-10)
    for rating in ratings.values():
        assert 0 <= rating <= 10
    
    # Check recommendations is a list
    assert isinstance(data["recommendations"], list)


def test_validate_prompt_with_context():
    """Test prompt validation with context."""
    request_data = {
        "prompt": "Analyze user purchase patterns",
        "context": {
            "data_source": "anonymized_transactions",
            "purpose": "market_research"
        }
    }
    
    response = client.post("/validate_prompt", json=request_data)
    assert response.status_code == 200
    data = response.json()
    
    assert "score" in data
    assert 0 <= data["score"] <= 100


def test_validate_prompt_missing_prompt():
    """Test validation fails when prompt is missing."""
    request_data = {}
    
    response = client.post("/validate_prompt", json=request_data)
    assert response.status_code == 422  # Validation error


def test_validate_prompt_empty_prompt():
    """Test validation with empty prompt."""
    request_data = {
        "prompt": ""
    }
    
    response = client.post("/validate_prompt", json=request_data)
    # Service should still accept empty string, but may give low scores
    assert response.status_code == 200


def test_llm_evaluator_custom_headers_parsing():
    """Test that custom headers are correctly parsed."""
    # Test single header
    evaluator = LLMEvaluator(
        endpoint="http://example.com",
        custom_headers="X-Ztat-Token:abc123"
    )
    assert evaluator.custom_headers == {"X-Ztat-Token": "abc123"}
    
    # Test multiple headers
    evaluator = LLMEvaluator(
        endpoint="http://example.com",
        custom_headers="X-Ztat-Token:abc123,X-Custom-Header:xyz789"
    )
    assert evaluator.custom_headers == {
        "X-Ztat-Token": "abc123",
        "X-Custom-Header": "xyz789"
    }
    
    # Test empty headers
    evaluator = LLMEvaluator(
        endpoint="http://example.com",
        custom_headers=""
    )
    assert evaluator.custom_headers == {}
    
    # Test headers with spaces
    evaluator = LLMEvaluator(
        endpoint="http://example.com",
        custom_headers="X-Ztat-Token: abc123 , X-Custom-Header: xyz789 "
    )
    assert evaluator.custom_headers == {
        "X-Ztat-Token": "abc123",
        "X-Custom-Header": "xyz789"
    }


def test_llm_evaluator_custom_headers_with_api_key():
    """Test that custom headers and API key can coexist."""
    evaluator = LLMEvaluator(
        endpoint="http://example.com",
        api_key="sk-test123",
        custom_headers="X-Ztat-Token:abc123"
    )
    assert evaluator.custom_headers == {"X-Ztat-Token": "abc123"}
    assert evaluator.api_key == "sk-test123"
