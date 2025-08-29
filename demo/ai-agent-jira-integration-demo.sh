#!/bin/bash

# AI Agent JIRA Integration Demo Script
# This script demonstrates how AI agents can discover and call JIRA capabilities

echo "=== AI Agent JIRA Integration Demo ==="
echo
echo "This demo shows how AI agents can discover and interact with JIRA capabilities"
echo "through the ai-agent module integration."
echo

# Build the project
echo "1. Building ai-agent module with JIRA integration..."
mvn clean compile -pl ai-agent -am -q

if [ $? -eq 0 ]; then
    echo "✓ Build successful"
else
    echo "✗ Build failed"
    exit 1
fi

# Run tests
echo
echo "2. Running AI Agent JIRA integration tests..."
mvn test -pl ai-agent -am -q

if [ $? -eq 0 ]; then
    echo "✓ All tests passed"
else
    echo "✗ Some tests failed"
    exit 1
fi

echo
echo "3. AI Agent JIRA Capabilities Summary:"
echo "   The ai-agent module now includes:"
echo "   • AIAgentJiraIntegrationService - bridges ai-agent with JIRA capabilities"
echo "   • AIAgentJiraVerbService - provides @Verb methods for AI agents"
echo "   • VerbRegistry extended to scan JIRA verbs in dataplane module"
echo "   • Comprehensive tests for all integration scenarios"
echo

echo "4. Available JIRA Verbs for AI Agents:"
echo "   • searchJiraTickets - Search for tickets using JQL or simple text"
echo "   • assignJiraTicket - Assign tickets to users"
echo "   • updateJiraTicket - Add comments to tickets"
echo "   • checkJiraAvailability - Check if JIRA integration is configured"
echo

echo "5. AI Agent Discovery Flow:"
echo "   1. AI Agent starts and calls VerbRegistry.scanClasspath()"
echo "   2. VerbRegistry discovers JIRA verbs from dataplane module"
echo "   3. AI Agent can check JIRA availability: checkJiraAvailability()"
echo "   4. AI Agent can search tickets: searchJiraTickets(query)"
echo "   5. AI Agent can assign tickets: assignJiraTicket(ticketKey, user)"
echo "   6. AI Agent can update tickets: updateJiraTicket(ticketKey, user, message)"
echo

echo "6. Integration Features:"
echo "   • Conditional loading - only available when JIRA is configured"
echo "   • Graceful degradation - returns empty results when JIRA unavailable"
echo "   • Zero breaking changes - builds on existing verb system"
echo "   • AI-callable verbs - all marked with isAiCallable = true"
echo

echo "✓ AI Agent JIRA Integration Demo Complete"
echo
echo "The ai-agent module now has flexible access to JIRA capabilities!"