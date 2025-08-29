package io.sentrius.sso.core.repository;

import java.util.Optional;
import io.sentrius.sso.core.model.security.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTypeRepository extends JpaRepository<UserType, Long> {


    Optional<UserType> findByUserTypeName(String userTypeName);

}
