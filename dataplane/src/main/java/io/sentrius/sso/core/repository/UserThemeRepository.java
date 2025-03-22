package io.sentrius.sso.core.repository;

import io.sentrius.sso.core.model.users.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserThemeRepository extends JpaRepository<UserSettings, Long> {
    // You can add custom query methods here if needed
}
