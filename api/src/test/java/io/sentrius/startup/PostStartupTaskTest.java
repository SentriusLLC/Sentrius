package io.sentrius.startup;

import com.jcraft.jsch.JSchException;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.ConfigurationOption;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.*;
import io.sentrius.sso.core.services.HostGroupService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.security.CryptoService;
import io.sentrius.sso.startup.ConfigurationApplicationTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class PostStartupTaskTest {

    @Mock
    private SystemOptions systemOptions;

    @Mock
    private ConfigurationOptionRepository configurationOptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HostGroupRepository hostGroupRepository;

    @Mock
    private SystemRepository systemRepository;

    @Mock
    private UserTypeRepository userTypeRepository;

    @Mock
    private UserService userService;

    @Mock
    private HostGroupService hostGroupService;

    @Mock
    private CryptoService cryptoService;

    @InjectMocks
    private ConfigurationApplicationTask postStartupTask;

    @BeforeEach
    void setUp() {
        // Set up default mocks if necessary

        Mockito.when(systemOptions.getYamlConfiguration()).thenReturn(getClass().getClassLoader().getResource(
            "configs" +
            "/exampleInstall.yml").getPath());

    }

    @Test
    void testAfterStartupWithYamlConfiguration()
        throws IOException, GeneralSecurityException, JSchException, SQLException {
        Mockito.when(systemOptions.getDeleteYamlConfigurationFile()).thenReturn(false);
        // Mock behavior for configurationOptionRepository
        var mockConfigOption = new ConfigurationOption();
        mockConfigOption.setConfigurationName("yamlConfigurationFileHash");
        mockConfigOption.setConfigurationValue("oldHash");

        Mockito.when(configurationOptionRepository.findLatestByConfigurationName("yamlConfigurationFileHash"))
            .thenReturn(Optional.of(mockConfigOption));

        Mockito.when(userService.addUscer(ArgumentMatchers.any(User.class))).thenReturn(User.builder().id(1L).name("name").build());
        // Call the method
        postStartupTask.afterStartup();

        // Verify interactions
        Mockito.verify(configurationOptionRepository).findLatestByConfigurationName("yamlConfigurationFileHash");
        Mockito.verify(configurationOptionRepository).save(mockConfigOption);

        // Assertions
        Assertions.assertNotEquals("oldHash", mockConfigOption.getConfigurationValue());
    }

    @Test
    void testAfterStartupNoYamlConfiguration() throws IOException, GeneralSecurityException, JSchException,
        SQLException {
        Mockito.when(systemOptions.getYamlConfiguration()).thenReturn(null);

        // Call the method
        postStartupTask.afterStartup();

        // Verify no interactions with repositories
        Mockito.verify(configurationOptionRepository, Mockito.never()).findLatestByConfigurationName(
            ArgumentMatchers.anyString());
    }
}
