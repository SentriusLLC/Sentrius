package io.sentrius.sso.core.model.categorization;

import io.sentrius.sso.core.dto.CommandCategoryDTO;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@NoArgsConstructor
@ToString
@AllArgsConstructor
@Entity
@Table(name = "command_categories")
public class CommandCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String categoryName;
    private String pattern;
    private int priority;

    public static CommandCategory fromDTO(CommandCategoryDTO category) {
        return CommandCategory.builder()
                .categoryName(category.getCategoryName())
                .pattern(category.getPattern())
                .priority(category.getPriority())
                .build();
    }

    public CommandCategoryDTO toDTO() {
        return CommandCategoryDTO.builder()
                .categoryName(categoryName)
                .pattern(pattern)
                .priority(priority)
                .build();
    }
}
