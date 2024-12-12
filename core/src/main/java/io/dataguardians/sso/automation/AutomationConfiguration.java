package io.dataguardians.sso.automation;

import io.dataguardians.sso.config.Configuration;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@Setter
public class AutomationConfiguration extends Configuration<AutomationPlugin> {}
