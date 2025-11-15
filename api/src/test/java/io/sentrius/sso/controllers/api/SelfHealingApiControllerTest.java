package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig.PatchingPolicy;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.selfhealing.ErrorAnalysisService;
import io.sentrius.sso.core.services.selfhealing.SelfHealingConfigService;
import io.sentrius.sso.core.services.selfhealing.SelfHealingSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelfHealingApiControllerTest {

    @Mock
    private SelfHealingConfigService configService;

    @Mock
    private SelfHealingSessionService sessionService;

    @Mock
    private ErrorAnalysisService errorAnalysisService;

    @Mock
    private ErrorOutputService errorOutputService;

    @org.mockito.InjectMocks
    private SelfHealingApiController controller;

    @Test
    void testGetAllConfigs() {
        SelfHealingConfig config = SelfHealingConfig.builder()
                .id(1L)
                .podName("test-pod")
                .patchingPolicy(PatchingPolicy.IMMEDIATE)
                .enabled(true)
                .build();

        when(configService.getAllConfigs()).thenReturn(Arrays.asList(config));

        ResponseEntity<List<SelfHealingConfig>> response = controller.getAllConfigs();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("test-pod", response.getBody().get(0).getPodName());
        assertEquals(PatchingPolicy.IMMEDIATE, response.getBody().get(0).getPatchingPolicy());
        verify(configService).getAllConfigs();
    }

    @Test
    void testGetConfigByPodName() {
        SelfHealingConfig config = SelfHealingConfig.builder()
                .id(1L)
                .podName("test-pod")
                .patchingPolicy(PatchingPolicy.OFF_HOURS)
                .enabled(true)
                .build();

        when(configService.getConfigByPodName("test-pod")).thenReturn(Optional.of(config));

        ResponseEntity<SelfHealingConfig> response = controller.getConfigByPodName("test-pod");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("test-pod", response.getBody().getPodName());
        assertEquals(PatchingPolicy.OFF_HOURS, response.getBody().getPatchingPolicy());
        verify(configService).getConfigByPodName("test-pod");
    }

    @Test
    void testSaveConfig() {
        SelfHealingConfig config = SelfHealingConfig.builder()
                .podName("new-pod")
                .patchingPolicy(PatchingPolicy.IMMEDIATE)
                .enabled(true)
                .build();

        SelfHealingConfig savedConfig = SelfHealingConfig.builder()
                .id(1L)
                .podName("new-pod")
                .patchingPolicy(PatchingPolicy.IMMEDIATE)
                .enabled(true)
                .build();

        when(configService.saveConfig(any(SelfHealingConfig.class))).thenReturn(savedConfig);

        ResponseEntity<SelfHealingConfig> response = controller.saveConfig(config);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("new-pod", response.getBody().getPodName());
        verify(configService).saveConfig(any(SelfHealingConfig.class));
    }

    @Test
    void testDeleteConfig() {
        doNothing().when(configService).deleteConfig(1L);

        ResponseEntity<Void> response = controller.deleteConfig(1L);

        assertEquals(200, response.getStatusCode().value());
        verify(configService).deleteConfig(1L);
    }

    @Test
    void testGetAllSessions() {
        SelfHealingSession session = SelfHealingSession.builder()
                .id(1L)
                .podName("test-pod")
                .status(SelfHealingSession.HealingStatus.ANALYZING)
                .build();

        when(sessionService.getAllSessions()).thenReturn(Arrays.asList(session));

        ResponseEntity<List<SelfHealingSession>> response = controller.getAllSessions();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(1L, response.getBody().get(0).getId());
        assertEquals("test-pod", response.getBody().get(0).getPodName());
        verify(sessionService).getAllSessions();
    }
}
