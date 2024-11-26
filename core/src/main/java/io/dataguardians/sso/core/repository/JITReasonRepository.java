package io.dataguardians.sso.core.repository;

import java.util.List;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.zt.JITReason;
import io.dataguardians.sso.core.model.zt.JITRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JITReasonRepository extends JpaRepository<JITReason, Long> {

}