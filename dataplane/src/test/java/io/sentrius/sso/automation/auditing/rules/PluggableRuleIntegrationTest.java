package io.sentrius.sso.automation.auditing.rules;

import io.sentrius.sso.automation.auditing.AccessTokenEvaluator;
import io.sentrius.sso.automation.auditing.RuleFactory;
import io.sentrius.sso.automation.auditing.SessionTokenEvaluator;
import io.sentrius.sso.automation.auditing.Trigger;
import io.sentrius.sso.automation.auditing.TriggerAction;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConnectedSystem;
import io.sentrius.sso.core.model.auditing.Rule;
import io.sentrius.sso.core.services.PluggableServices;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating the full pluggable rule system including:
 * - Rule loading via RuleFactory
 * - Multiple rule types working together
 * - Synchronous rule evaluation
 */
@ExtendWith(MockitoExtension.class)
class PluggableRuleIntegrationTest {

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ConnectedSystem connectedSystem;

    @Mock
    private SessionTrackingService sessionTrackingService;

    private Map<String, PluggableServices> pluggableServicesMap;

    @BeforeEach
    void setUp() {
        pluggableServicesMap = new HashMap<>();
    }

    @Test
    void testRuleFactoryLoadsPluggableRules() throws Exception {
        // Create rule definitions
        List<Rule> initialRules = new ArrayList<>();
        
        Rule pluggableRule = Rule.builder()
            .id(1L)
            .displayNm("Block Sudo")
            .ruleClass("io.sentrius.sso.automation.auditing.rules.PluggableRuleEvaluator")
            .ruleConfig("#text.contains('sudo'):DENY:Sudo not allowed:true")
            .build();
        
        initialRules.add(pluggableRule);

        // Prepare collections for rules
        List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();
        List<SessionTokenEvaluator> beforeAndAfterRules = new ArrayList<>();

        // Load rules via RuleFactory
        RuleFactory.createRules(
            systemOptions,
            connectedSystem,
            sessionTrackingService,
            initialRules,
            synchronousRules,
            beforeAndAfterRules,
            pluggableServicesMap
        );

        // Verify rule was loaded
        assertEquals(1, synchronousRules.size());
        assertTrue(synchronousRules.get(0) instanceof PluggableRuleEvaluator);

        // Test the loaded rule
        AccessTokenEvaluator loadedRule = synchronousRules.get(0);
        Optional<Trigger> result = loadedRule.trigger("sudo rm -rf /");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.DENY_ACTION, result.get().getAction());
        assertEquals("Sudo not allowed", result.get().getDescription());
    }

    @Test
    void testMultiplePluggableRulesLoadAndEvaluate() throws Exception {
        List<Rule> initialRules = new ArrayList<>();
        
        // Add multiple pluggable rules
        initialRules.add(Rule.builder()
            .id(1L)
            .displayNm("Block Sudo")
            .ruleClass("io.sentrius.sso.automation.auditing.rules.PluggableRuleEvaluator")
            .ruleConfig("#text.contains('sudo'):DENY:Sudo not allowed:true")
            .build());
        
        initialRules.add(Rule.builder()
            .id(2L)
            .displayNm("Warn on RM")
            .ruleClass("io.sentrius.sso.automation.auditing.rules.PluggableRuleEvaluator")
            .ruleConfig("#text.contains('rm'):WARN:Delete command detected:true")
            .build());
        
        initialRules.add(Rule.builder()
            .id(3L)
            .displayNm("Require Approval for passwd")
            .ruleClass("io.sentrius.sso.automation.auditing.rules.PluggableRuleEvaluator")
            .ruleConfig("#text.contains('passwd'):JIT:Password change requires approval:true")
            .build());

        List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();
        List<SessionTokenEvaluator> beforeAndAfterRules = new ArrayList<>();

        RuleFactory.createRules(
            systemOptions,
            connectedSystem,
            sessionTrackingService,
            initialRules,
            synchronousRules,
            beforeAndAfterRules,
            pluggableServicesMap
        );

        // Verify all rules loaded
        assertEquals(3, synchronousRules.size());

        // Test each rule
        String sudoCommand = "sudo apt update";
        String rmCommand = "rm file.txt";
        String passwdCommand = "passwd user";

        // Test sudo rule
        Optional<Trigger> sudoResult = synchronousRules.get(0).trigger(sudoCommand);
        assertTrue(sudoResult.isPresent());
        assertEquals(TriggerAction.DENY_ACTION, sudoResult.get().getAction());

        // Test rm rule
        Optional<Trigger> rmResult = synchronousRules.get(1).trigger(rmCommand);
        assertTrue(rmResult.isPresent());
        assertEquals(TriggerAction.WARN_ACTION, rmResult.get().getAction());

        // Test passwd rule
        Optional<Trigger> passwdResult = synchronousRules.get(2).trigger(passwdCommand);
        assertTrue(passwdResult.isPresent());
        assertEquals(TriggerAction.JIT_ACTION, passwdResult.get().getAction());
    }

    @Test
    void testMixedPluggableAndStandardRules() throws Exception {
        List<Rule> initialRules = new ArrayList<>();
        
        // Add pluggable rule
        initialRules.add(Rule.builder()
            .id(1L)
            .displayNm("Block Sudo (Pluggable)")
            .ruleClass("io.sentrius.sso.automation.auditing.rules.PluggableRuleEvaluator")
            .ruleConfig("#text.toLowerCase().contains('sudo'):DENY:Sudo not allowed:true")
            .build());
        
        // Add standard CommandEvaluator rule
        initialRules.add(Rule.builder()
            .id(2L)
            .displayNm("Block RM (Standard)")
            .ruleClass("io.sentrius.sso.automation.auditing.rules.CommandEvaluator")
            .ruleConfig("rm:DENY:Delete not allowed")
            .build());

        List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();
        List<SessionTokenEvaluator> beforeAndAfterRules = new ArrayList<>();

        RuleFactory.createRules(
            systemOptions,
            connectedSystem,
            sessionTrackingService,
            initialRules,
            synchronousRules,
            beforeAndAfterRules,
            pluggableServicesMap
        );

        // Verify both rules loaded
        assertEquals(2, synchronousRules.size());
        assertTrue(synchronousRules.get(0) instanceof PluggableRuleEvaluator);
        assertTrue(synchronousRules.get(1) instanceof CommandEvaluator);

        // Test pluggable rule (case insensitive)
        Optional<Trigger> sudoResult = synchronousRules.get(0).trigger("SUDO command");
        assertTrue(sudoResult.isPresent());
        assertEquals(TriggerAction.DENY_ACTION, sudoResult.get().getAction());

        // Test standard rule
        Optional<Trigger> rmResult = synchronousRules.get(1).trigger("rm file.txt");
        assertTrue(rmResult.isPresent());
        assertEquals(TriggerAction.DENY_ACTION, rmResult.get().getAction());
    }

    @Test
    void testComplexExpressionRule() throws Exception {
        List<Rule> initialRules = new ArrayList<>();
        
        // Complex rule with multiple conditions
        initialRules.add(Rule.builder()
            .id(1L)
            .displayNm("Complex Security Rule")
            .ruleClass("io.sentrius.sso.automation.auditing.rules.PluggableRuleEvaluator")
            .ruleConfig("(#text.contains('sudo') or #text.contains('su ')) and #length > 10:DENY:Privilege escalation attempt detected:true")
            .build());

        List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();
        List<SessionTokenEvaluator> beforeAndAfterRules = new ArrayList<>();

        RuleFactory.createRules(
            systemOptions,
            connectedSystem,
            sessionTrackingService,
            initialRules,
            synchronousRules,
            beforeAndAfterRules,
            pluggableServicesMap
        );

        AccessTokenEvaluator rule = synchronousRules.get(0);

        // Should trigger: contains sudo AND length > 10
        Optional<Trigger> result1 = rule.trigger("sudo apt update");
        assertTrue(result1.isPresent());

        // Should trigger: contains su AND length > 10
        Optional<Trigger> result2 = rule.trigger("su root shell");
        assertTrue(result2.isPresent());

        // Should NOT trigger: contains sudo but length <= 10
        Optional<Trigger> result3 = rule.trigger("sudo ls");
        assertFalse(result3.isPresent());

        // Should NOT trigger: length > 10 but no sudo or su
        Optional<Trigger> result4 = rule.trigger("ls -la /home/user");
        assertFalse(result4.isPresent());
    }

    @Test
    void testRuleWithInvalidExpression() {
        List<Rule> initialRules = new ArrayList<>();
        
        // Rule with invalid expression (should not load)
        initialRules.add(Rule.builder()
            .id(1L)
            .displayNm("Invalid Rule")
            .ruleClass("io.sentrius.sso.automation.auditing.rules.PluggableRuleEvaluator")
            .ruleConfig("T(java.lang.Runtime):DENY:Should be blocked")
            .build());

        List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();
        List<SessionTokenEvaluator> beforeAndAfterRules = new ArrayList<>();

        // Should not throw exception, but should not load the rule
        assertDoesNotThrow(() -> {
            RuleFactory.createRules(
                systemOptions,
                connectedSystem,
                sessionTrackingService,
                initialRules,
                synchronousRules,
                beforeAndAfterRules,
                pluggableServicesMap
            );
        });

        // Rule should not have been loaded due to security validation failure
        // The RuleFactory logs the error but continues
        assertTrue(synchronousRules.isEmpty() || 
                  synchronousRules.get(0).describeAction() == TriggerAction.NO_ACTION);
    }

    @Test
    void testRuleConfigurationPersistence() throws Exception {
        // Test that rule configuration is properly stored and can be re-loaded
        String originalConfig = "#text.contains('forbidden'):DENY:Forbidden command:true";
        
        Rule rule = Rule.builder()
            .id(1L)
            .displayNm("Test Rule")
            .ruleClass("io.sentrius.sso.automation.auditing.rules.PluggableRuleEvaluator")
            .ruleConfig(originalConfig)
            .build();

        List<Rule> initialRules = List.of(rule);
        List<AccessTokenEvaluator> synchronousRules = new ArrayList<>();
        List<SessionTokenEvaluator> beforeAndAfterRules = new ArrayList<>();

        RuleFactory.createRules(
            systemOptions,
            connectedSystem,
            sessionTrackingService,
            initialRules,
            synchronousRules,
            beforeAndAfterRules,
            pluggableServicesMap
        );

        // Verify the rule loaded correctly and works as expected
        AccessTokenEvaluator loadedRule = synchronousRules.get(0);
        Optional<Trigger> result = loadedRule.trigger("forbidden action");
        
        assertTrue(result.isPresent());
        assertEquals(TriggerAction.DENY_ACTION, result.get().getAction());
        assertEquals("Forbidden command", result.get().getDescription());
    }
}
