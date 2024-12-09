package io.dataguardians.sso.install.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jcraft.jsch.JSchException;
import io.dataguardians.ConfiguredClass;
import io.dataguardians.sso.core.model.security.enums.RuleAccessEnum;
import io.dataguardians.sso.core.model.security.enums.SSHAccessEnum;
import io.dataguardians.sso.core.utils.KeyUtil;
import org.junit.jupiter.api.Test;

public class InstallConfigurationTest extends ConfiguredClass {

    static ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    @Test
    public void testDeserializeWrong() throws IOException, SQLException, GeneralSecurityException {
        InputStream inputStream =
            Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("configs/exampleWrongInstall.yml");

        assertThrows(
            UnrecognizedPropertyException.class,
            () -> objectMapper.readValue(inputStream, InstallConfiguration.class));
    }

    @Test
    public void testA() {}

    @Test
    public void testDeserialize() throws IOException {
        InputStream inputStream =
            Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("configs/exampleInstall.yml");

        InstallConfiguration configuration =
            objectMapper.readValue(inputStream, InstallConfiguration.class);
        assertNotNull(configuration);
        assertEquals("root", configuration.getSystems().get(0).getSshUser());
        assertEquals("name", configuration.getUsers().get(0).getName());

    }

    @Test
    public void testDeserializeTyped() throws IOException {
        InputStream inputStream =
            Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("configs/exampleInstallWithTypes.yml");

        InstallConfiguration configuration =
            objectMapper.readValue(inputStream, InstallConfiguration.class);
        assertNotNull(configuration);
        assertEquals("marc", configuration.getSystems().get(0).getSshUser());
        assertEquals("name", configuration.getUsers().get(0).getName());
        var userType = configuration.getUsers().get(0).getAuthorizationType();

        assertNotNull(userType);

        var setType = configuration.getUserTypes().get(0);

        assertEquals(userType.getUserTypeName(), setType.getUserTypeName());
        assertEquals(SSHAccessEnum.CAN_MANAGE_SYSTEMS, setType.getSystemAccess());
        assertEquals(RuleAccessEnum.CAN_DEL_RULES, setType.getRuleAccess());

    }

    /*
    @Test
    public void testConfigureH2()
        throws IOException,
        JSchException,
        SQLException,
        ConfigurationException,
        GeneralSecurityException {
        InputStream inputStream =
            Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("configs/exampleInstallWithTypes.yml");
        DBInitializer.createTables();
        DatabaseConfiguration databaseConfiguration = new DatabaseConfiguration(inputStream);
        var connection = DBUtils.getConn();
        databaseConfiguration.initialize(connection);
        User user = UserDB.getUser(2L);
        assertEquals("firstname", user.getFirstNm());
        assertEquals("lastname", user.getLastNm());
        assertEquals("testType", user.getAuthorizationType().getUserTypeName());
        var profile = user.getProfileList().get(0);
        assertEquals("testGroup", profile.getNm());
        assertEquals(1, ProfileSystemsDB.getSystemIdsByProfile(profile.getId(), user.getId()).size());
    }
*/
    @Test
    public void testCrypto() throws IOException, JSchException {
        InputStream inputStream =
            Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("configs/exampleInstallWithTypes.yml");

        InstallConfiguration configuration =
            objectMapper.readValue(inputStream, InstallConfiguration.class);
        assertNotNull(configuration);

        var sysKeys = configuration.getSystemKeyConfigurations();

        assertNotNull(sysKeys);

        var sysKey = sysKeys.get(0);

        var priv = sysKey.getPrivateKey();

        var pub = sysKey.getPublicKey();

        var passphase = sysKey.getPrivateKeyPassphrase();

        KeyUtil.validateKeyPair(priv.getBytes(), pub.getBytes(), passphase);
    }

}
