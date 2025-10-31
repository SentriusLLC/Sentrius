package io.sentrius.agent.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ZtatAsessment {
    String requestId;
    boolean approved;
    boolean denied;
    String questionToAgent;

}
