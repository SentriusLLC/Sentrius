/**
 * Copyright (C) 2013 Loophole, LLC
 *
 * <p>Licensed under The Prosperity Public License 3.0.0
 */
package io.dataguardians.sso.core.model.auditing;

import io.dataguardians.sso.automation.auditing.rules.CommandEvaluator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Value object that contains configuration information around auditing rules */
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Rule {
  Long id;
  String displayNm;
  String ruleClass = CommandEvaluator.class.getCanonicalName();
  String ruleConfig;

  String errorMsg;
}
