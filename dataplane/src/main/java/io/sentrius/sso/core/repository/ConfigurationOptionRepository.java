package io.sentrius.sso.core.repository;

import java.util.List;
import java.util.Optional;
import io.sentrius.sso.core.model.ConfigurationOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfigurationOptionRepository extends JpaRepository<ConfigurationOption, Long> {

    @Query("SELECT c FROM ConfigurationOption c WHERE c.configurationName = :configurationName ORDER BY c.id DESC LIMIT 1")
    Optional<ConfigurationOption> findLatestByConfigurationName(@Param("configurationName") String configurationName);

    @Query("SELECT c FROM ConfigurationOption c WHERE c.podName = :podName AND c.configurationName = :configurationName ORDER BY c.id DESC LIMIT 1")
    Optional<ConfigurationOption> findLatestByPodNameAndConfigurationName(
        @Param("podName") String podName,
        @Param("configurationName") String configurationName);

    @Query("SELECT c FROM ConfigurationOption c WHERE c.podName = :podName")
    List<ConfigurationOption> findAllByPodName(@Param("podName") String podName);

    @Query("SELECT c FROM ConfigurationOption c WHERE c.podName IS NULL")
    List<ConfigurationOption> findAllGlobal();

    @Query("SELECT c FROM ConfigurationOption c WHERE c.podName IS NULL AND c.configurationName = :configurationName ORDER BY c.id DESC LIMIT 1")
    Optional<ConfigurationOption> findLatestGlobalByConfigurationName(@Param("configurationName") String configurationName);
}
