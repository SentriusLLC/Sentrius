package io.sentrius.sso.core.repository;

import java.util.List;
import java.util.Optional;
import io.sentrius.sso.core.model.AgentPolicyAssignment;
import io.sentrius.sso.core.model.AgentPolicyAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentPolicyAssignmentRepository extends JpaRepository<AgentPolicyAssignment, AgentPolicyAssignmentId> {
    // Add custom queries here if needed

    List<AgentPolicyAssignment> findByUserUsernameOrderByAssignedAtDesc(String username);

    @Query("SELECT a FROM AgentPolicyAssignment a WHERE a.user.id = :userId ORDER BY a.assignedAt DESC")
    Optional<AgentPolicyAssignment> findMostRecentByUserId(@Param("userId") Long userId);
}

