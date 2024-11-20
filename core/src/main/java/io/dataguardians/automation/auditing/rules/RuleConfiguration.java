package io.dataguardians.automation.auditing.rules;

import io.dataguardians.automation.auditing.AuditorRule;
import io.dataguardians.config.Configuration;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@Setter
public class RuleConfiguration extends Configuration<AuditorRule> {}
