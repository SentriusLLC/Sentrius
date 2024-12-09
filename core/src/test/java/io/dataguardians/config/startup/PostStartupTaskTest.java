package io.dataguardians.config.startup;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.jcraft.jsch.JSchException;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.model.ConfigurationOption;
import io.dataguardians.sso.core.repository.*;
import io.dataguardians.sso.core.security.service.CryptoService;
import io.dataguardians.sso.core.services.HostGroupService;
import io.dataguardians.sso.core.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
    private PostStartupTask postStartupTask;

    @BeforeEach
    void setUp() {
        // Set up default mocks if necessary
        when(systemOptions.getYamlConfiguration()).thenReturn("configs/exampleInstall.yml");
        when(systemOptions.deleteYamlConfigurationFile).thenReturn(true);
    }

    @Test
    void testAfterStartupWithYamlConfiguration()
        throws IOException, GeneralSecurityException, JSchException, SQLException {
        // Mock behavior for configurationOptionRepository
        var mockConfigOption = new ConfigurationOption();
        mockConfigOption.setConfigurationName("yamlConfigurationFileHash");
        mockConfigOption.setConfigurationValue("oldHash");

        when(configurationOptionRepository.findByConfigurationName("yamlConfigurationFileHash"))
            .thenReturn(Optional.of(mockConfigOption));

        // Call the method
        postStartupTask.afterStartup();

        // Verify interactions
        verify(configurationOptionRepository).findByConfigurationName("yamlConfigurationFileHash");
        verify(configurationOptionRepository).save(mockConfigOption);

        // Assertions
        assertNotEquals("oldHash", mockConfigOption.getConfigurationValue());
    }

    @Test
    void testAfterStartupNoYamlConfiguration() throws IOException, GeneralSecurityException, JSchException,
        SQLException {
        when(systemOptions.getYamlConfiguration()).thenReturn(null);

        // Call the method
        postStartupTask.afterStartup();

        // Verify no interactions with repositories
        verify(configurationOptionRepository, never()).findByConfigurationName(anyString());
    }
}
