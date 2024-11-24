package io.dataguardians.sso.core.security;

import io.dataguardians.sso.core.config.SystemOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JITConfigProvider implements io.dataguardians.config.security.zt.JITConfigProvider {
    static {
        System.setProperty("JIT_CONFIG_CLASS", JITConfigProvider.class.getCanonicalName());
    }
    
    final SystemOptions options;
    @Override
    public Integer getMaxJitUses() {
        return options.maxJitUses;
    }

    @Override
    public Integer getMaxJitDurationMs() {
        return options.getMaxJitDurationMs();
    }

    @Override
    public Integer getApprovedJITPeriod() {
        return options.getApprovedJITPeriod();
    }

    @Override
    public boolean getJitRequiresTicket() {
        return options.getJitRequiresTicket();
    }
}
