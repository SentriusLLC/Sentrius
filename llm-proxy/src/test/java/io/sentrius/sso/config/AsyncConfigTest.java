package io.sentrius.sso.config;

import io.sentrius.sso.core.services.TerminalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncConfigTest {

    @Mock
    private TerminalService terminalService;

    private AsyncConfig asyncConfig;

    @BeforeEach
    void setUp() {
        asyncConfig = new AsyncConfig();
        // Use reflection to set the private field
        try {
            var field = AsyncConfig.class.getDeclaredField("terminalService");
            field.setAccessible(true);
            field.set(asyncConfig, terminalService);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Handle reflection error in test setup
        }
    }

    @Test
    void taskExecutorBeanCreatesThreadPoolTaskExecutor() {
        Executor executor = asyncConfig.taskExecutor();

        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(15, taskExecutor.getCorePoolSize());
        assertEquals(20, taskExecutor.getMaxPoolSize());
        assertEquals(100, taskExecutor.getQueueCapacity());
        assertEquals("SentriusTask-", taskExecutor.getThreadNamePrefix());
    }

    @Test
    void shutdownExecutorCallsTerminalServiceShutdown() {
        asyncConfig.shutdownExecutor();
        
        verify(terminalService).shutdown();
    }

    @Test
    void asyncConfigCanBeInstantiated() {
        AsyncConfig config = new AsyncConfig();
        assertNotNull(config);
    }

    @Test
    void taskExecutorCreatesNewInstanceEachTime() {
        Executor executor1 = asyncConfig.taskExecutor();
        Executor executor2 = asyncConfig.taskExecutor();

        assertNotNull(executor1);
        assertNotNull(executor2);
        assertNotSame(executor1, executor2);
    }
}