package io.sentrius.sso.core.repository;

import java.util.List;
import java.util.Optional;
import io.sentrius.sso.core.model.WorkHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkHoursRepository extends JpaRepository<WorkHours, Long> {

    @Query("SELECT w FROM WorkHours w WHERE w.user.id = :userId " +
        "AND FUNCTION('TIME', CURRENT_TIMESTAMP) BETWEEN w.startTime AND w.endTime")
    List<WorkHours> findWorkHoursByTime(@Param("userId") Long userId);

    List<WorkHours> findByUserId(Long userId);
}
