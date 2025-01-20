package io.sentrius.sso.core.repository;

import java.util.List;
import io.sentrius.sso.core.model.categorization.CommandCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CommandCategoryRepository extends JpaRepository<CommandCategory, Long> {
    @Query("SELECT c FROM CommandCategory c ORDER BY c.priority ASC")
    List<CommandCategory> findAllOrderedByPriority();

    List<CommandCategory> findByPattern(String pattern);
}
