package io.sentrius.sso.core.services.selfhealing;

import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig;
import io.sentrius.sso.core.model.selfhealing.SelfHealingConfig.PatchingPolicy;
import io.sentrius.sso.core.repository.selfhealing.SelfHealingConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelfHealingConfigServiceTest {

    @Mock
    private SelfHealingConfigRepository configRepository;

    @InjectMocks
    private SelfHealingConfigService configService;

    private SelfHealingConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = SelfHealingConfig.builder()
                .id(1L)
                .podName("test-pod")
                .podType("api")
                .patchingPolicy(PatchingPolicy.IMMEDIATE)
                .enabled(true)
                .build();
    }

    @Test
    void testGetPatchingPolicyForPod_Exists() {
        when(configRepository.findByPodName("test-pod")).thenReturn(Optional.of(testConfig));

        PatchingPolicy policy = configService.getPatchingPolicyForPod("test-pod");

        assertEquals(PatchingPolicy.IMMEDIATE, policy);
        verify(configRepository).findByPodName("test-pod");
    }

    @Test
    void testGetPatchingPolicyForPod_NotExists() {
        when(configRepository.findByPodName("unknown-pod")).thenReturn(Optional.empty());

        PatchingPolicy policy = configService.getPatchingPolicyForPod("unknown-pod");

        assertEquals(PatchingPolicy.NEVER, policy);
        verify(configRepository).findByPodName("unknown-pod");
    }

    @Test
    void testIsHealingEnabledForPod_Enabled() {
        when(configRepository.findByPodName("test-pod")).thenReturn(Optional.of(testConfig));

        boolean enabled = configService.isHealingEnabledForPod("test-pod");

        assertTrue(enabled);
    }

    @Test
    void testIsHealingEnabledForPod_Disabled() {
        testConfig.setEnabled(false);
        when(configRepository.findByPodName("test-pod")).thenReturn(Optional.of(testConfig));

        boolean enabled = configService.isHealingEnabledForPod("test-pod");

        assertFalse(enabled);
    }

    @Test
    void testIsHealingEnabledForPod_NotExists() {
        when(configRepository.findByPodName("unknown-pod")).thenReturn(Optional.empty());

        boolean enabled = configService.isHealingEnabledForPod("unknown-pod");

        assertFalse(enabled);
    }

    @Test
    void testSaveConfig() {
        when(configRepository.save(any(SelfHealingConfig.class))).thenReturn(testConfig);

        SelfHealingConfig saved = configService.saveConfig(testConfig);

        assertNotNull(saved);
        assertEquals("test-pod", saved.getPodName());
        assertNotNull(saved.getUpdatedAt());
        verify(configRepository).save(any(SelfHealingConfig.class));
    }
}
