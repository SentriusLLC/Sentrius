package io.sentrius.sso.automation.auditing;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import io.sentrius.sso.core.services.PluggableServices;
import io.sentrius.sso.protobuf.Session;

public abstract class SessionTokenEvaluator extends AccessTokenEvaluator {

    protected Map<String,PluggableServices> pluggableServices = new HashMap<>();


    public abstract Optional<Trigger> onMessage(Session.TerminalMessage text);

    public abstract boolean isOnlySessionRule();

    public void setPluggableServices(Map<String,PluggableServices> pluggableServices){
        this.pluggableServices = pluggableServices;
    }
}
