package io.dataguardians.sso.core.config;

import io.dataguardians.sso.core.services.TerminalService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    private ThreadPoolTaskExecutor executor;

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(15);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("SecureShellTask-");
        executor.initialize();
        return executor;
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (executor != null) {
            executor.shutdown();
        }
        log.info("Shuttind down executor");
        // Call shutdown on SshListenerService to close streams
        terminalService.shutdown();
    }

    @Autowired
    private TerminalService terminalService;
}
