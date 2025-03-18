package io.sentrius.sso.automation.auditing.rules;

import io.sentrius.sso.automation.auditing.AccessTokenEvaluator;
import io.sentrius.sso.config.Configuration;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@Setter
public class RuleConfiguration extends Configuration<AccessTokenEvaluator> {}
