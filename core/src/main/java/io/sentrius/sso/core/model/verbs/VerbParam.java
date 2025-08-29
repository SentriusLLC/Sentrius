package io.sentrius.sso.core.model.verbs;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Builder
@Data
@Getter
@Setter
public class VerbParam {
    private String name;
    private String type;
    private boolean required;
}
