package io.dataguardians.sso.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "twopartyapproval")
public class TwoPartyApprovalProperties {

    private Map<String, Boolean> option;
    private Map<String, Boolean> requireExplanation;

    public TwoPartyApprovalProperties() {
        option = new HashMap<>();
        requireExplanation = new HashMap<>();
    }

    public Map<String, Boolean> getOption() {
        return option;
    }

    public void setOption(Map<String, Boolean> option) {
        this.option = option;
    }

    public Map<String, Boolean> getRequireExplanation() {
        return requireExplanation;
    }

    public void setRequireExplanation(Map<String, Boolean> requireExplanation) {
        this.requireExplanation = requireExplanation;
    }
}
