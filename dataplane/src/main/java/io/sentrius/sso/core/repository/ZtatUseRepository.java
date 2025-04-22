package io.sentrius.sso.core.repository;

import java.util.List;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenApproval;
import io.sentrius.sso.core.model.zt.ZtatUse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ZtatUseRepository extends JpaRepository<ZtatUse, Long> {


    @Query("SELECT ztu FROM ZtatUse ztu WHERE ztu.ztatApproval = :ztatApproval")
    List<ZtatUse> getUses(ZeroTrustAccessTokenApproval ztatApproval);
}