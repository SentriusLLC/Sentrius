package io.sentrius.sso.core.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@Builder
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomAttributeMappingDTO {

    private Long id;
    private String endpoint;
    private String attributeName;
    private String requiredValue;
    private String description;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
