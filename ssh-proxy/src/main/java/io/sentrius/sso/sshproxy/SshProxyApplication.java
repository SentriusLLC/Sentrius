package io.sentrius.sso.sshproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "io.sentrius.sso.sshproxy",
    "io.sentrius.sso.core",
    "io.sentrius.sso.automation"
})
public class SshProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SshProxyApplication.class, args);
    }
}