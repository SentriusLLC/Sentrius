package io.dataguardians.sso.core.services;
import io.dataguardians.sso.core.config.TwoPartyApprovalProperties;
import io.dataguardians.sso.core.model.security.enums.SystemOperationsEnum;
import io.dataguardians.sso.core.model.security.enums.TwoPartyApprovalConfig;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class TwoPartyApprovalConfigService {

    private final TwoPartyApprovalProperties properties;
    private Map<SystemOperationsEnum, TwoPartyApprovalConfig> approvalConfigMap;

    public TwoPartyApprovalConfigService(TwoPartyApprovalProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        approvalConfigMap = loadTwoPartyApprovalMapping();
    }

    private Map<SystemOperationsEnum, TwoPartyApprovalConfig> loadTwoPartyApprovalMapping() {
        Map<SystemOperationsEnum, TwoPartyApprovalConfig> configMap = new HashMap<>();

        for (SystemOperationsEnum operation : SystemOperationsEnum.values()) {
            var builder = TwoPartyApprovalConfig.builder();

            // Use properties from TwoPartyApprovalProperties
            String operationName = operation.toString().toUpperCase();

            if (properties.getOption().containsKey(operationName)) {
                builder.requireApproval(properties.getOption().get(operationName));
            }

            if (properties.getRequireExplanation().containsKey(operationName)) {
                builder.requireExplanation(properties.getRequireExplanation().get(operationName));
            }

            configMap.put(operation, builder.build());
        }

        return configMap;
    }

    public TwoPartyApprovalConfig getApprovalConfig(SystemOperationsEnum operation) {
        if (approvalConfigMap == null) {
            throw new IllegalStateException("Approval configuration map has not been initialized.");
        }
        return approvalConfigMap.get(operation);
    }
}

