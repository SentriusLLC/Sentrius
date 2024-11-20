package io.sentrius.sso.core.model.security.enums;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TwoPartyApprovalConfig {
  @Builder.Default private boolean requireApproval = false;
  @Builder.Default private boolean requireExplanation = false;
}
