package io.sentrius.sso.core.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
@Builder
@Data
public class CommandCategoryDTO {
        private Long id;
        private String categoryName;
        private String pattern;
        private int priority;

}
