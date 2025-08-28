package io.sentrius.sso.sshproxy.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import io.sentrius.sso.core.services.TerminalService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class TaskConfig {

    private final TerminalService terminalService;

    // Keep a reference so we can shut it down explicitly on destroy, if desired.
    private ThreadPoolTaskExecutor executor;

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(15);
        exec.setMaxPoolSize(20);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("ProxySession-");
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        exec.initialize();

        this.executor = exec; // assign the field, not a shadowed local
        return exec;          // expose as Executor for @Async
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (executor != null) {
            log.info("Shutting down task executor");
            executor.shutdown();
        }
        // If you truly want this on application shutdown:
        log.info("Shutting down TerminalService");
        terminalService.shutdown();
    }
}