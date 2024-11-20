package io.dataguardians.sso.core.repository;

import java.util.List;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileRepository extends JpaRepository<HostGroup, Long> {

    HostGroup getById(Long profileId);

    List<HostGroup> findAll();
}
