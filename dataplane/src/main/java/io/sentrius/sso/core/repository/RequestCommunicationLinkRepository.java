package io.sentrius.sso.core.repository;

import java.util.List;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.zt.OpsZeroTrustAcessTokenRequest;
import io.sentrius.sso.core.model.zt.RequestCommunicationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestCommunicationLinkRepository extends JpaRepository<RequestCommunicationLink, Long> {

}