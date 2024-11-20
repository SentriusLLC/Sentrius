package io.dataguardians.sso.core.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DtoHasAccess {
    @Builder.Default
    boolean canEdit = false;
    @Builder.Default
    boolean canView = false;
    boolean canDelete = false;
}
