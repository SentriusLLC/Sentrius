package io.dataguardians.sso.core.repository;

import java.util.List;
import io.dataguardians.sso.core.model.HostSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemRepository extends JpaRepository<HostSystem, Long> {

    List<HostSystem> findAll();

}
