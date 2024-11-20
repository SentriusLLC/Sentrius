package io.dataguardians.sso.core.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User getById(Long userId);

    Optional<User> findByUsername(String username);


    @Query("SELECT u.hostGroups FROM User u WHERE u.id = :userId")
    Set<HostGroup> findHostGroupsByUserId(@Param("userId") Long userId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.authorizationType")
    List<User> findAllWithAuthorizationType();
}
