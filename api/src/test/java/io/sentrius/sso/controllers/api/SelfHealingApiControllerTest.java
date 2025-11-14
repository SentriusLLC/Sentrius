package io.sentrius.sso.controllers.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig.PatchingPolicy;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.selfhealing.ErrorAnalysisService;
import io.sentrius.sso.core.services.selfhealing.SelfHealingConfigService;
import io.sentrius.sso.core.services.selfhealing.SelfHealingSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SelfHealingApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class SelfHealingApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SelfHealingConfigService configService;

    @MockBean
    private SelfHealingSessionService sessionService;

    @MockBean
    private ErrorAnalysisService errorAnalysisService;

    @MockBean
    private ErrorOutputService errorOutputService;

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_APPLICATION")
    void testGetAllConfigs() throws Exception {
        SelfHealingConfig config = SelfHealingConfig.builder()
                .id(1L)
                .podName("test-pod")
                .patchingPolicy(PatchingPolicy.IMMEDIATE)
                .enabled(true)
                .build();

        when(configService.getAllConfigs()).thenReturn(Arrays.asList(config));

        mockMvc.perform(get("/api/v1/self-healing/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].podName").value("test-pod"))
                .andExpect(jsonPath("$[0].patchingPolicy").value("IMMEDIATE"));

        verify(configService).getAllConfigs();
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_APPLICATION")
    void testGetConfigByPodName() throws Exception {
        SelfHealingConfig config = SelfHealingConfig.builder()
                .id(1L)
                .podName("test-pod")
                .patchingPolicy(PatchingPolicy.OFF_HOURS)
                .enabled(true)
                .build();

        when(configService.getConfigByPodName("test-pod")).thenReturn(Optional.of(config));

        mockMvc.perform(get("/api/v1/self-healing/config/test-pod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podName").value("test-pod"))
                .andExpect(jsonPath("$.patchingPolicy").value("OFF_HOURS"));

        verify(configService).getConfigByPodName("test-pod");
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_APPLICATION")
    void testSaveConfig() throws Exception {
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

        mockMvc.perform(post("/api/v1/self-healing/config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.podName").value("new-pod"));

        verify(configService).saveConfig(any(SelfHealingConfig.class));
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_APPLICATION")
    void testDeleteConfig() throws Exception {
        doNothing().when(configService).deleteConfig(1L);

        mockMvc.perform(delete("/api/v1/self-healing/config/1")
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(configService).deleteConfig(1L);
    }

    @Test
    @WithMockUser(authorities = "CAN_MANAGE_APPLICATION")
    void testGetAllSessions() throws Exception {
        SelfHealingSession session = SelfHealingSession.builder()
                .id(1L)
                .podName("test-pod")
                .status(SelfHealingSession.HealingStatus.ANALYZING)
                .build();

        when(sessionService.getAllSessions()).thenReturn(Arrays.asList(session));

        mockMvc.perform(get("/api/v1/self-healing/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].podName").value("test-pod"));

        verify(sessionService).getAllSessions();
    }
}
