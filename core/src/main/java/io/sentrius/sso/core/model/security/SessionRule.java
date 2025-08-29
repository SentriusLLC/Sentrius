package io.sentrius.sso.core.model.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class SessionRule {
    String sessionRuleName;
    String sessionRuleClass;
}
