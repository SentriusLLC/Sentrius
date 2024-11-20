package io.dataguardians.sso.core.repository;

import io.dataguardians.sso.core.model.security.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTypeRepository extends JpaRepository<UserType, Long> {


}
