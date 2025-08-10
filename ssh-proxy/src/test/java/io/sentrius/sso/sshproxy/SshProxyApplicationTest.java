package io.sentrius.sso.sshproxy;

import io.sentrius.sso.sshproxy.service.InlineTerminalResponseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {InlineTerminalResponseService.class})
@TestPropertySource(properties = {
    "sentrius.ssh-proxy.enabled=false"
})
class SshProxyApplicationTest {

    @Autowired
    private InlineTerminalResponseService terminalResponseService;

    @Test
    void contextLoads() {
        assertNotNull(terminalResponseService);
    }
}