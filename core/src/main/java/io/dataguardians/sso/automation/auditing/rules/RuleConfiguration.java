package io.dataguardians.sso.automation.auditing.rules;

import io.dataguardians.sso.automation.auditing.AccessTokenEvaluator;
import io.dataguardians.sso.config.Configuration;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@Setter
public class RuleConfiguration extends Configuration<AccessTokenEvaluator> {}
