package io.sentrius.sso.automation.auditing.rules;

import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.config.SystemOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PluggableRuleEvaluatorTest {

    @Mock
    private SystemOptions systemOptions;

    private PluggableRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PluggableRuleEvaluator();
    }

    @Test
    void testConfigureWithValidExpression() {
        String config = "#text.contains('sudo'):DENY:Sudo is not allowed:true";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertTrue(result);
        assertEquals(TriggerAction.DENY_ACTION, evaluator.describeAction());
        assertTrue(evaluator.requiresSanitized());
    }

    @Test
    void testConfigureWithoutSanitizedFlag() {
        String config = "#text.contains('admin'):WARN:Admin access detected";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertTrue(result);
        assertEquals(TriggerAction.WARN_ACTION, evaluator.describeAction());
        assertTrue(evaluator.requiresSanitized()); // Default is true
    }

    @Test
    void testConfigureWithFalseSanitizedFlag() {
        String config = "#text.contains('test'):LOG:Test command:false";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertTrue(result);
        assertEquals(TriggerAction.LOG_ACTION, evaluator.describeAction());
        assertFalse(evaluator.requiresSanitized());
    }

    @Test
    void testConfigureWithInvalidFormat() {
        String config = "invalid:format";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testConfigureWithNullConfiguration() {
        boolean result = evaluator.configure(systemOptions, null);
        
        assertFalse(result);
    }

    @Test
    void testConfigureWithEmptyConfiguration() {
        boolean result = evaluator.configure(systemOptions, "");
        
        assertFalse(result);
    }

    @Test
    void testTriggerWithMatchingExpression() {
        String config = "#text.contains('sudo'):DENY:Sudo is not allowed";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("sudo rm -rf /");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.DENY_ACTION, result.get().getAction());
        assertEquals("Sudo is not allowed", result.get().getDescription());
    }

    @Test
    void testTriggerWithNonMatchingExpression() {
        String config = "#text.contains('sudo'):DENY:Sudo is not allowed";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("ls -la");
        
        assertFalse(result.isPresent());
    }

    @Test
    void testTriggerWithNullText() {
        String config = "#text.contains('sudo'):DENY:Sudo is not allowed";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger(null);
        
        assertFalse(result.isPresent());
    }

    @Test
    void testTriggerBeforeConfiguration() {
        Optional<Trigger> result = evaluator.trigger("any text");
        
        assertFalse(result.isPresent());
    }

    @Test
    void testComplexExpression() {
        String config = "#length > 10 and #text.contains('rm'):WARN:Long rm command";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("rm -rf /some/long/path");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.WARN_ACTION, result.get().getAction());
    }

    @Test
    void testLengthVariable() {
        String config = "#length > 50:WARN:Command too long";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("a".repeat(51));
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.WARN_ACTION, result.get().getAction());
    }

    @Test
    void testIsEmptyVariable() {
        String config = "#isEmpty:LOG:Empty command";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.LOG_ACTION, result.get().getAction());
    }

    @Test
    void testIsBlankVariable() {
        String config = "#isBlank:LOG:Blank command";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("   ");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.LOG_ACTION, result.get().getAction());
    }

    @Test
    void testJitAction() {
        String config = "#text.contains('passwd'):JIT:Password change requires approval";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("passwd user");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.JIT_ACTION, result.get().getAction());
    }

    @Test
    void testAlertAction() {
        String config = "#text.contains('DROP TABLE'):ALERT:Dangerous SQL command detected";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("DROP TABLE users");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.ALERT_ACTION, result.get().getAction());
    }

    @Test
    void testPromptAction() {
        String config = "#text.contains('reboot'):PROMPT:Reboot requires confirmation";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("reboot now");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.PROMPT_ACTION, result.get().getAction());
    }

    @Test
    void testPersistentMessage() {
        String config = "#text.contains('critical'):PERSISTENT:Critical operation detected";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result = evaluator.trigger("critical system check");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.PERSISTENT_MESSAGE, result.get().getAction());
    }

    @Test
    void testSecurityValidationBlocksTypeReference() {
        String config = "T(java.lang.Runtime):DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksGetClass() {
        String config = "#text.getClass():DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksBeanReference() {
        String config = "@systemBean:DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksNewOperator() {
        String config = "new java.util.ArrayList():DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksRuntime() {
        String config = "Runtime.getRuntime():DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksSystemAccess() {
        String config = "System.exit(0):DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksProcessAccess() {
        String config = "ProcessBuilder:DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksClassReference() {
        String config = "String.class:DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksJavaLangAccess() {
        String config = "java.lang.String:DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksIOAccess() {
        String config = "java.io.File:DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testSecurityValidationBlocksNetworkAccess() {
        String config = "java.net.URL:DENY:Should be blocked";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testCaseInsensitiveContains() {
        String config = "#text.toLowerCase().contains('sudo'):DENY:Case insensitive sudo check";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result1 = evaluator.trigger("SUDO command");
        Optional<Trigger> result2 = evaluator.trigger("SuDo command");
        Optional<Trigger> result3 = evaluator.trigger("sudo command");
        
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertTrue(result3.isPresent());
    }

    @Test
    void testMultipleConditionsWithAnd() {
        String config = "#length > 5 and #text.contains('rm'):DENY:Short rm commands only";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result1 = evaluator.trigger("rm -rf /");
        Optional<Trigger> result2 = evaluator.trigger("rm f");
        
        assertTrue(result1.isPresent()); // Length > 5 and contains 'rm'
        assertFalse(result2.isPresent()); // Length <= 5
    }

    @Test
    void testMultipleConditionsWithOr() {
        String config = "#text.contains('sudo') or #text.contains('su '):DENY:Privilege escalation";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result1 = evaluator.trigger("sudo command");
        Optional<Trigger> result2 = evaluator.trigger("su root");
        Optional<Trigger> result3 = evaluator.trigger("ls -la");
        
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertFalse(result3.isPresent());
    }

    @Test
    void testNegationOperator() {
        String config = "!#isEmpty:LOG:Non-empty command";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result1 = evaluator.trigger("command");
        Optional<Trigger> result2 = evaluator.trigger("");
        
        assertTrue(result1.isPresent());
        assertFalse(result2.isPresent());
    }

    @Test
    void testStartsWithPattern() {
        String config = "#text.startsWith('rm '):WARN:Remove command detected";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result1 = evaluator.trigger("rm -rf /");
        Optional<Trigger> result2 = evaluator.trigger("chmod rm");
        
        assertTrue(result1.isPresent());
        assertFalse(result2.isPresent());
    }

    @Test
    void testEndsWithPattern() {
        String config = "#text.endsWith('&'):WARN:Background process detected";
        evaluator.configure(systemOptions, config);
        
        Optional<Trigger> result1 = evaluator.trigger("long_running_command &");
        Optional<Trigger> result2 = evaluator.trigger("& at start");
        
        assertTrue(result1.isPresent());
        assertFalse(result2.isPresent());
    }

    @Test
    void testInvalidActionHandling() {
        String config = "#text.contains('test'):INVALID_ACTION:Test";
        
        boolean result = evaluator.configure(systemOptions, config);
        
        assertFalse(result);
    }

    @Test
    void testExpressionEvaluationError() {
        // Force configuration with a potentially problematic expression
        String config = "#nonexistentVariable > 5:WARN:Should handle gracefully";
        evaluator.configure(systemOptions, config);
        
        // Should return empty instead of throwing exception
        Optional<Trigger> result = evaluator.trigger("test");
        
        assertFalse(result.isPresent());
    }
}
