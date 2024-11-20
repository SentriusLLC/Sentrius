package io.dataguardians.config.security.zt;

public class DefaultJITConfigProvider implements JITConfigProvider{
    @Override
    public Integer getMaxJitUses() {
        return 1;
    }

    @Override
    public Integer getMaxJitDurationMs() {
        return 1440*1000;
    }

    @Override
    public Integer getApprovedJITPeriod() {
        return 60;
    }

    @Override
    public boolean getJitRequiresTicket() {
        return true;
    }
}
