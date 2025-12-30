package io.sentrius.sso.core.data.attributes;

import java.util.HashMap;
import java.util.Map;

public final  class UIResourceMappings {

    static final Map<String, UIResourceConfig> mappings = new HashMap<>();

    static final Map<String, UIResourceConfig> mappingsByKey = new HashMap<>();
    static {

        // Infrastructure menu items
        mappings.put("infrastructure", new UIResourceConfig("CAN_VIEW_SYSTEMS", "/ui/infrastructure", ""));
        mappings.put("infrastructure.hosts", new UIResourceConfig("CAN_VIEW_SYSTEMS", "/ui/infrastructure/hosts",
            "/sso/v1/enclaves/hosts/list"));
        mappings.put("infrastructure.integrations", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui" +
            "/infrastructure/integrations", "/sso/v1/integrations"));

        // Security & Access menu items
        mappings.put("security", new UIResourceConfig("CAN_VIEW_RULES", "/ui/security", ""));
        mappings.put("security.rules", new UIResourceConfig("CAN_VIEW_RULES", "/ui/security/rules", "/sso/v1/zerotrust/rules/list"));
        mappings.put("security.trust_policies", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui/security" +
            "/trust_policies", "/sso/v1/atpl/"));
        mappings.put("security.trust_scores", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui/security" +
            "/trust_scores", "/sso/trust-scores"));
        mappings.put("security.attributes", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui/security/attributes",
            "/sso/v1/attributes/manage"   ));

        // AI & Agents menu items
        mappings.put("ai", new UIResourceConfig("CAN_MANAGE_USERS", "/ui/ai", ""));
        mappings.put("ai.services", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui/ai/services", "/sso/v1/ai/services"));
        mappings.put("ai.manage_users", new UIResourceConfig("CAN_MANAGE_USERS", "/ui/ai/manage_users", "/sso/v1/users/list"));
        mappings.put("ai.agent_templates", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui/ai/agent_templates", "/sso/v1/agent/templates"));
        mappings.put("ai.prompt_advisor", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui/ai/prompt_advisor", "/sso/v1/prompt-advisor"));
        mappings.put("ai.agent_memory", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui/ai/agent_memory", "/sso/v1/agent/memory/search"));

        // System menu items
        mappings.put("system", new UIResourceConfig("CAN_MANAGE_SYSTEMS", "/ui/system", "" ));
        mappings.put("system.settings", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui/system/settings", "/sso/v1/system/settings"));
        mappings.put("system.telemetry", new UIResourceConfig("CAN_MANAGE_APPLICATION", "/ui/system/telemetry", "/sso/v1/telemetry"));
        mappings.put("system.automation", new UIResourceConfig("CAN_MANAGE_SYSTEMS", "/ui/system/automation", "/sso/v1/automation/suggestions/list"));
        mappings.put("system.pods", new UIResourceConfig("CAN_MANAGE_SYSTEMS", "/ui/system/pods", "/sso/v1/k8s/pods/logs"));

        mappings.forEach((mappingKey, uiResourceConfig) -> {
            mappingsByKey.put(uiResourceConfig.getAbacResource(), uiResourceConfig);
            if (!uiResourceConfig.getUiMapping().isEmpty()) {
                mappingsByKey.put(uiResourceConfig.getUiMapping(), uiResourceConfig);
            }
        });

    }
    /**
        * Define UI resource mappings with their access requirements and ABAC resources
     */
    public static Map<String, UIResourceConfig> getUIResourceMappings() {
        return mappings;
    }

    public static UIResourceConfig getByUIResourceKey(String key) {
        var resp = mappingsByKey.get(key);
        if (resp != null){
            return resp;
        }
        for (Map.Entry<String, UIResourceConfig> entry : mappingsByKey.entrySet()) {
                if (key.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
        }
        return null;
    }
}
