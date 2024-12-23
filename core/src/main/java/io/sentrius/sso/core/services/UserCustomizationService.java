package io.sentrius.sso.core.services;

import io.sentrius.sso.core.model.users.UserSettings;
import io.sentrius.sso.core.repository.UserThemeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserCustomizationService {

    @Autowired
    private UserThemeRepository userThemeRepository;

    public Optional<UserSettings> getUserSettingsById(Long userId) {
        return userThemeRepository.findById(userId);
    }

    public UserSettings saveUserTheme(UserSettings userTheme) {
        return userThemeRepository.save(userTheme);
    }
}
