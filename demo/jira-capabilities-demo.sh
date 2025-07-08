#!/bin/bash

# JIRA Capabilities Integration Demonstration Script
# This script demonstrates the AI agent workflow for discovering and using JIRA capabilities

echo "==========================================="
echo "JIRA Capabilities Integration Demonstration"
echo "==========================================="
echo

echo "This demonstrates how an AI agent would discover and use JIRA capabilities:"
echo

echo "1. AI Agent discovers available capabilities:"
echo "   GET /api/v1/capabilities/verbs"
echo "   -> Returns list of all AI-callable verb methods"
echo

echo "2. AI Agent finds JIRA-related verbs in the response:"
echo "   - searchForTickets: Search for JIRA tickets using query"
echo "   - assignTicket: Assign a ticket to a user"
echo "   - updateTicket: Add a comment to a ticket"
echo "   - isJiraAvailable: Check if JIRA integration is configured"
echo

echo "3. AI Agent checks JIRA availability:"
echo "   CALL isJiraAvailable()"
echo "   -> Returns true if JIRA integration is configured"
echo

echo "4. AI Agent searches for tickets:"
echo "   CALL searchForTickets('project = SUPPORT AND status = Open')"
echo "   -> Returns list of matching tickets:"
echo "      [{"
echo "        \"id\": \"SUPPORT-123\","
echo "        \"summary\": \"Login issue reported\","
echo "        \"status\": \"Open\","
echo "        \"type\": \"jira\""
echo "      }]"
echo

echo "5. AI Agent assigns a ticket:"
echo "   CALL assignTicket('SUPPORT-123', currentUser)"
echo "   -> Returns true if assignment successful"
echo

echo "6. AI Agent adds a comment:"
echo "   CALL updateTicket('SUPPORT-123', currentUser, 'Investigating this issue')"
echo "   -> Returns true if comment added successfully"
echo

echo "==========================================="
echo "Implementation Benefits:"
echo "==========================================="
echo "✓ AI agents can discover JIRA capabilities automatically"
echo "✓ Works whether JIRA is configured or not"
echo "✓ Uses existing security and authentication"
echo "✓ Minimal changes to existing codebase"
echo "✓ Consistent with other Sentrius capabilities"
echo

echo "==========================================="
echo "Key Components Implemented:"
echo "==========================================="
echo "✓ JiraVerbService - Exposes JIRA operations as @Verb methods"
echo "✓ Conditional availability checking"
echo "✓ AI-callable annotations (isAiCallable = true)"
echo "✓ Integration with existing CapabilitiesApiController"
echo "✓ Comprehensive unit tests"
echo "✓ Documentation and examples"
echo

echo "Ready for AI agent integration!"