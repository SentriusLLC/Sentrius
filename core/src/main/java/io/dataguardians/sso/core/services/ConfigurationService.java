package io.dataguardians.sso.core.services;

import io.dataguardians.sso.core.model.install.Configuration;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.repository.ConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;

    public ConfigurationService(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    @Transactional
    public Configuration saveOrUpdateConfiguration(User user, String configName, String content) {
        Optional<Configuration> existingConfig = configurationRepository.findByConfigNameAndUserId(configName,
            user.getId());

        Configuration configuration;
        if (existingConfig.isPresent()) {
            // Update existing configuration
            configuration = existingConfig.get();
            configuration.setContent(content);
            configuration.setUpdatedAt(LocalDateTime.now());
        } else {
            // Save new configuration
            configuration = new Configuration();
            configuration.setUser(user);
            configuration.setConfigName(configName);
            configuration.setContent(content);
        }

        return configurationRepository.save(configuration);
    }

    public Optional<Configuration> findById(Long databaseId) {
        return configurationRepository.findById(databaseId);
    }
}

