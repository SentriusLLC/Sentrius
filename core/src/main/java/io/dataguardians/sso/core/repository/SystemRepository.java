package io.dataguardians.sso.core.repository;

import java.util.List;
import io.dataguardians.sso.core.model.HostSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemRepository extends JpaRepository<HostSystem, Long> {

    List<HostSystem> findAll();

    List<HostSystem> findByDisplayName(String systemObj);

    List<HostSystem> findByDisplayNameAndHost(String displayName, String host);

    @Query("SELECT COUNT(hs) > 0 FROM HostSystem hs JOIN hs.hostGroups hg WHERE hs.id = :hostSystemId AND hg.id IN :hostGroupIds")
    boolean isAssignedToHostGroups(@Param("hostSystemId") Long hostSystemId, @Param("hostGroupIds") List<Long> hostGroupIds);

}
