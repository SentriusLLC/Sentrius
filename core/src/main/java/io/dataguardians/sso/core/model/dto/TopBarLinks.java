package io.dataguardians.sso.core.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Getter
@Data
@AllArgsConstructor
public class TopBarLinks {
    private String url;
    private String name;
    private String id;
}
