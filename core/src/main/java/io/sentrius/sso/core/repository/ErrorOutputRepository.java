// ErrorOutputRepository
package io.sentrius.sso.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import io.sentrius.sso.core.model.ErrorOutput;

@Repository
public interface ErrorOutputRepository extends JpaRepository<ErrorOutput, Long> {
    Page<ErrorOutput> findAllByOrderByLogTmDesc(PageRequest pageRequest);
}
