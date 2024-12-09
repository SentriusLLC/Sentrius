package io.dataguardians.sso.core.repository;

import java.util.List;
import java.util.Optional;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostGroupRepository extends JpaRepository<HostGroup, Long>, JpaSpecificationExecutor<HostGroup> {


    @Query("SELECT hg FROM HostGroup hg JOIN hg.users u WHERE u.id = :userId")
    List<HostGroup> findAllByUserId(@Param("userId") Long userId);

    @Query("SELECT hg FROM HostGroup hg LEFT JOIN FETCH hg.users WHERE hg.id = :id")
    Optional<HostGroup> findByIdWithUsers(@Param("id") Long id);

    @Query("SELECT hg FROM HostGroup hg LEFT JOIN FETCH hg.hostSystemList WHERE hg.id = :hostGroupId")
    HostGroup findHostGroupWithHostSystemsById(@Param("hostGroupId") Long hostGroupId);
}