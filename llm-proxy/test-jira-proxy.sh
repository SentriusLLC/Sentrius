#!/bin/bash

# JIRA Proxy API Test Script
# This script demonstrates how to interact with the JIRA proxy endpoints

# Configuration
BASE_URL="http://localhost:8080"
JWT_TOKEN="your-jwt-token-here"

echo "Testing JIRA Proxy API Endpoints"
echo "================================="

# Test 1: Search for issues
echo -e "\n1. Testing search endpoint..."
curl -X GET \
  "${BASE_URL}/api/v1/jira/rest/api/3/search?query=test" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  --silent --show-error || echo "Search test failed (expected if no auth)"

# Test 2: Get specific issue
echo -e "\n2. Testing get issue endpoint..."
curl -X GET \
  "${BASE_URL}/api/v1/jira/rest/api/3/issue/TEST-123" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  --silent --show-error || echo "Get issue test failed (expected if no auth)"

# Test 3: Add comment
echo -e "\n3. Testing add comment endpoint..."
curl -X POST \
  "${BASE_URL}/api/v1/jira/rest/api/3/issue/TEST-123/comment" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"text": "Test comment from compliance agent"}' \
  --silent --show-error || echo "Add comment test failed (expected if no auth)"

# Test 4: Assign issue
echo -e "\n4. Testing assign issue endpoint..."
curl -X PUT \
  "${BASE_URL}/api/v1/jira/rest/api/3/issue/TEST-123/assignee" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"accountId": "test-user-id"}' \
  --silent --show-error || echo "Assign issue test failed (expected if no auth)"

echo -e "\n\nAPI endpoint testing completed."
echo "Note: These tests will fail with authentication errors unless you have:"
echo "1. A running instance of the llm-proxy service"
echo "2. A valid JWT token"
echo "3. A configured JIRA integration"

# Test with invalid token to verify security
echo -e "\nTesting security with invalid token..."
curl -X GET \
  "${BASE_URL}/api/v1/jira/rest/api/3/search?query=test" \
  -H "Authorization: Bearer invalid-token" \
  -H "Content-Type: application/json" \
  --include --silent --show-error | head -1

echo -e "\nExpected: HTTP 401 Unauthorized for invalid token"