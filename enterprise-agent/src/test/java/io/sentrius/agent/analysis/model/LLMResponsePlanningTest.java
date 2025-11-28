package io.sentrius.agent.analysis.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sentrius.sso.core.utils.JsonUtil;

/**
 * Tests for the improved planning semantics in LLMResponse.
 * These tests verify the plan state tracking, executed operations management,
 * and conversational vs action-required response detection.
 */
class LLMResponsePlanningTest {

    @Test
    void testPlanStatusDefaultsToIdle() {
        LLMResponse response = LLMResponse.builder().build();
        assertEquals("idle", response.getPlanStatus());
    }

    @Test
    void testExecutedOperationsDefaultsToEmptyList() {
        LLMResponse response = LLMResponse.builder().build();
        assertNotNull(response.getExecutedOperations());
        assertTrue(response.getExecutedOperations().isEmpty());
    }

    @Test
    void testIsConversationalOnlyWithCompletedStatus() {
        LLMResponse response = LLMResponse.builder()
            .planStatus("completed")
            .nextOperation("")
            .responseForUser("Thank you!")
            .build();
        
        assertTrue(response.isConversationalOnly());
    }

    @Test
    void testIsConversationalOnlyWithIdleStatus() {
        LLMResponse response = LLMResponse.builder()
            .planStatus("idle")
            .nextOperation(null)
            .responseForUser("Hello!")
            .build();
        
        assertTrue(response.isConversationalOnly());
    }

    @Test
    void testIsNotConversationalOnlyWhenNextOperationPresent() {
        LLMResponse response = LLMResponse.builder()
            .planStatus("in_progress")
            .nextOperation("create_agent")
            .responseForUser("Creating agent...")
            .build();
        
        assertFalse(response.isConversationalOnly());
    }

    @Test
    void testRequiresExecutionWithValidOperation() {
        LLMResponse response = LLMResponse.builder()
            .planStatus("in_progress")
            .nextOperation("execute_task")
            .build();
        
        assertTrue(response.requiresExecution());
    }

    @Test
    void testRequiresExecutionFalseWhenCompleted() {
        LLMResponse response = LLMResponse.builder()
            .planStatus("completed")
            .nextOperation("some_operation")
            .build();
        
        assertFalse(response.requiresExecution());
    }

    @Test
    void testRequiresExecutionFalseWithNoOperation() {
        LLMResponse response = LLMResponse.builder()
            .planStatus("in_progress")
            .nextOperation("")
            .build();
        
        assertFalse(response.requiresExecution());
    }

    @Test
    void testExecutedOperationsTracking() {
        List<String> executedOps = new ArrayList<>();
        executedOps.add("create_agent");
        executedOps.add("configure_agent");
        
        LLMResponse response = LLMResponse.builder()
            .planStatus("in_progress")
            .executedOperations(executedOps)
            .previousOperation("configure_agent")
            .nextOperation("start_agent")
            .build();
        
        assertEquals(2, response.getExecutedOperations().size());
        assertTrue(response.getExecutedOperations().contains("create_agent"));
        assertTrue(response.getExecutedOperations().contains("configure_agent"));
    }

    @Test
    void testPlanStatusSerialization() throws JsonProcessingException {
        LLMResponse response = LLMResponse.builder()
            .planStatus("in_progress")
            .previousOperation("step1")
            .nextOperation("step2")
            .responseForUser("Processing...")
            .build();
        
        String json = JsonUtil.MAPPER.writeValueAsString(response);
        assertTrue(json.contains("\"planStatus\":\"in_progress\""));
        
        LLMResponse deserialized = JsonUtil.MAPPER.readValue(json, LLMResponse.class);
        assertEquals("in_progress", deserialized.getPlanStatus());
    }

    @Test
    void testExecutedOperationsSerialization() throws JsonProcessingException {
        List<String> executedOps = new ArrayList<>();
        executedOps.add("op1");
        executedOps.add("op2");
        
        LLMResponse response = LLMResponse.builder()
            .executedOperations(executedOps)
            .build();
        
        String json = JsonUtil.MAPPER.writeValueAsString(response);
        assertTrue(json.contains("\"executedOperations\""));
        
        LLMResponse deserialized = JsonUtil.MAPPER.readValue(json, LLMResponse.class);
        assertEquals(2, deserialized.getExecutedOperations().size());
    }

    @Test
    void testConversationalFollowUpScenario() {
        // Simulates the "Thanks!" follow-up scenario
        // After a plan is completed, user says "Thanks!"
        LLMResponse response = LLMResponse.builder()
            .planStatus("completed")
            .previousOperation("create_agent_with_context")
            .nextOperation("")  // No next operation needed
            .responseForUser("You're welcome! The agent has been created successfully.")
            .summaryForLLM("User acknowledged completion of agent creation task.")
            .build();
        
        assertTrue(response.isConversationalOnly());
        assertFalse(response.requiresExecution());
    }

    @Test
    void testPlanInProgressScenario() {
        List<String> executedOps = new ArrayList<>();
        executedOps.add("get_endpoints_like");
        
        LLMResponse response = LLMResponse.builder()
            .planStatus("in_progress")
            .executedOperations(executedOps)
            .previousOperation("get_endpoints_like")
            .nextOperation("create_agent_with_context")
            .responseForUser("Found relevant endpoints. Now creating the agent...")
            .build();
        
        assertFalse(response.isConversationalOnly());
        assertTrue(response.requiresExecution());
        assertEquals(1, response.getExecutedOperations().size());
    }

    @Test
    void testAwaitingInputStatus() {
        LLMResponse response = LLMResponse.builder()
            .planStatus("awaiting_input")
            .nextOperation("")
            .responseForUser("What name would you like for the agent?")
            .build();
        
        // awaiting_input should not be considered conversational only
        // because it's waiting for specific input
        assertFalse(response.isConversationalOnly());
    }
    
    @Test
    void testAwaitingInputDoesNotRequireExecution() {
        // Even with a nextOperation specified, awaiting_input should not execute
        // until user provides input
        LLMResponse response = LLMResponse.builder()
            .planStatus("awaiting_input")
            .nextOperation("create_agent")
            .responseForUser("What name would you like for the agent?")
            .build();
        
        // awaiting_input should NOT require execution even with nextOperation
        assertFalse(response.requiresExecution());
    }

    @Test
    void testJsonIgnoreOnComputedProperties() throws JsonProcessingException {
        LLMResponse response = LLMResponse.builder()
            .planStatus("completed")
            .nextOperation("")
            .build();
        
        String json = JsonUtil.MAPPER.writeValueAsString(response);
        
        // The computed properties should not be serialized
        assertFalse(json.contains("conversationalOnly"));
        assertFalse(json.contains("requiresExecution"));
    }

    @Test
    void testFullPlanExecutionCycle() {
        // Step 1: Initial request
        LLMResponse step1 = LLMResponse.builder()
            .planStatus("in_progress")
            .executedOperations(new ArrayList<>())
            .nextOperation("get_endpoints_like")
            .responseForUser("Searching for relevant endpoints...")
            .build();
        
        assertTrue(step1.requiresExecution());
        
        // Step 2: After executing get_endpoints_like
        List<String> executedOps = new ArrayList<>();
        executedOps.add("get_endpoints_like");
        
        LLMResponse step2 = LLMResponse.builder()
            .planStatus("in_progress")
            .executedOperations(executedOps)
            .previousOperation("get_endpoints_like")
            .nextOperation("create_agent_with_context")
            .responseForUser("Found endpoints. Creating agent...")
            .build();
        
        assertTrue(step2.requiresExecution());
        assertEquals(1, step2.getExecutedOperations().size());
        
        // Step 3: After executing create_agent_with_context (plan complete)
        executedOps.add("create_agent_with_context");
        
        LLMResponse step3 = LLMResponse.builder()
            .planStatus("completed")
            .executedOperations(executedOps)
            .previousOperation("create_agent_with_context")
            .nextOperation("")
            .responseForUser("Agent created successfully!")
            .build();
        
        assertFalse(step3.requiresExecution());
        assertTrue(step3.isConversationalOnly());
        assertEquals(2, step3.getExecutedOperations().size());
        
        // Step 4: User says "Thanks!"
        LLMResponse step4 = LLMResponse.builder()
            .planStatus("completed")
            .executedOperations(executedOps)
            .previousOperation("create_agent_with_context")
            .nextOperation("")
            .responseForUser("You're welcome!")
            .build();
        
        assertTrue(step4.isConversationalOnly());
        assertFalse(step4.requiresExecution());
    }
    
    @Test
    void testAutonomousAgentCompletedStateReset() {
        // Simulates the scenario where autonomous agent completes a task
        // and should be ready to restart with reset state
        
        List<String> executedOps = new ArrayList<>();
        executedOps.add("list_active_terminal_sessions");
        
        LLMResponse completedResponse = LLMResponse.builder()
            .planStatus("completed")
            .executedOperations(executedOps)
            .previousOperation("list_active_terminal_sessions")
            .nextOperation("")  // Empty - plan is complete
            .responseForUser("No active SSH terminal sessions were found.")
            .summaryForLLM("No active SSH terminal sessions were found. Task is complete.")
            .build();
        
        // Verify the response is conversational only (doesn't require execution)
        assertTrue(completedResponse.isConversationalOnly());
        assertFalse(completedResponse.requiresExecution());
        assertEquals("completed", completedResponse.getPlanStatus());
        assertTrue(completedResponse.getNextOperation() == null || completedResponse.getNextOperation().isEmpty());
        
        // After state reset, a fresh response should start with idle status
        LLMResponse freshResponse = LLMResponse.builder()
            .planStatus("idle")
            .executedOperations(new ArrayList<>())  // Reset to empty
            .previousOperation("")
            .nextOperation("list_active_terminal_sessions")  // Starting fresh
            .build();
        
        assertFalse(freshResponse.isConversationalOnly());
        assertTrue(freshResponse.requiresExecution());
        assertEquals("idle", freshResponse.getPlanStatus());
        assertTrue(freshResponse.getExecutedOperations().isEmpty());
    }
}
