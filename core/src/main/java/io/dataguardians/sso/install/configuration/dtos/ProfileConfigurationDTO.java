package io.dataguardians.sso.install.configuration.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileConfigurationDTO {
    private String key;
    private String value;
}

