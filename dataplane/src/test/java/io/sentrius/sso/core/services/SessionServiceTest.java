package io.sentrius.sso.core.services;

import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.repository.SessionLogRepository;
import io.sentrius.sso.core.repository.TerminalLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionLogRepository sessionLogRepository;

    @Mock
    private TerminalLogRepository terminalLogRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        // Set the agentProxyExternalUrl using ReflectionTestUtils
        ReflectionTestUtils.setField(sessionService, "agentProxyExternalUrl", "http://test-agent-proxy");
        ReflectionTestUtils.setField(sessionService, "restTemplate", restTemplate);
    }

    @Test
    void testGetGraphDataWithoutAgentSessions() {
        // Given
        String username = "testuser";
        
        // Mock terminal session data
        when(sessionLogRepository.findByUsername(username)).thenReturn(Arrays.asList(
            createSessionLog(1L, username),
            createSessionLog(2L, username)
        ));
        
        when(terminalLogRepository.findMinAndMaxLogTmBySessionLogId(1L))
            .thenReturn(Arrays.asList(new Object[]{
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)),
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(5))
            }));
            
        when(terminalLogRepository.findMinAndMaxLogTmBySessionLogId(2L))
            .thenReturn(Arrays.asList(new Object[]{
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(20)),
                Timestamp.valueOf(LocalDateTime.now())
            }));

        // Mock agent service calls to return empty lists
        when(restTemplate.exchange(
            eq("http://test-agent-proxy/api/v1/sessions/agent/durations"),
            eq(HttpMethod.GET),
            isNull(),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(new ArrayList<>()));

        when(restTemplate.exchange(
            eq("http://test-agent-proxy/api/v1/sessions/agent/active-durations"),
            eq(HttpMethod.GET),
            isNull(),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(new ArrayList<>()));

        // When
        Map<String, Integer> result = sessionService.getGraphData(username);

        // Then
        assertNotNull(result);
        assertEquals(4, result.size());
        assertTrue(result.containsKey("0-5 min"));
        assertTrue(result.containsKey("5-15 min"));
        assertTrue(result.containsKey("15-30 min"));
        assertTrue(result.containsKey("30+ min"));
        
        // Should have 1 session in 0-5 min range and 1 in 15-30 min range
        assertEquals(1, result.get("0-5 min"));
        assertEquals(0, result.get("5-15 min"));
        assertEquals(1, result.get("15-30 min"));
        assertEquals(0, result.get("30+ min"));
    }

    @Test
    void testGetGraphDataWithAgentSessions() {
        // Given
        String username = "testuser";
        
        // Mock terminal session data (empty for simplicity)
        when(sessionLogRepository.findByUsername(username)).thenReturn(new ArrayList<>());

        // Mock agent session data
        List<Map<String, Object>> completedAgentSessions = Arrays.asList(
            createAgentSessionData("agent1", 3L),  // 0-5 min
            createAgentSessionData("agent2", 8L)   // 5-15 min
        );
        
        List<Map<String, Object>> activeAgentSessions = Arrays.asList(
            createAgentSessionData("agent3", 25L)  // 15-30 min
        );

        when(restTemplate.exchange(
            eq("http://test-agent-proxy/api/v1/sessions/agent/durations"),
            eq(HttpMethod.GET),
            isNull(),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(completedAgentSessions));

        when(restTemplate.exchange(
            eq("http://test-agent-proxy/api/v1/sessions/agent/active-durations"),
            eq(HttpMethod.GET),
            isNull(),
            any(ParameterizedTypeReference.class)
        )).thenReturn(ResponseEntity.ok(activeAgentSessions));

        // When
        Map<String, Integer> result = sessionService.getGraphData(username);

        // Then
        assertNotNull(result);
        assertEquals(1, result.get("0-5 min"));   // agent1
        assertEquals(1, result.get("5-15 min"));  // agent2  
        assertEquals(1, result.get("15-30 min")); // agent3
        assertEquals(0, result.get("30+ min"));
    }

    @Test
    void testGetGraphDataWithAgentProxyError() {
        // Given
        String username = "testuser";
        
        // Mock terminal session data (empty for simplicity)
        when(sessionLogRepository.findByUsername(username)).thenReturn(new ArrayList<>());

        // Mock agent service calls to throw exception
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            isNull(),
            any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("Connection failed"));

        // When
        Map<String, Integer> result = sessionService.getGraphData(username);

        // Then
        assertNotNull(result);
        // Should still return valid graph data even if agent service is unavailable
        assertEquals(4, result.size());
        assertEquals(0, result.get("0-5 min"));
        assertEquals(0, result.get("5-15 min"));
        assertEquals(0, result.get("15-30 min"));
        assertEquals(0, result.get("30+ min"));
    }

    @Test
    void testGetGraphDataWithEmptyAgentProxyUrl() {
        // Given
        String username = "testuser";
        ReflectionTestUtils.setField(sessionService, "agentProxyExternalUrl", "");
        
        // Mock terminal session data (empty for simplicity)
        when(sessionLogRepository.findByUsername(username)).thenReturn(new ArrayList<>());

        // When
        Map<String, Integer> result = sessionService.getGraphData(username);

        // Then
        assertNotNull(result);
        assertEquals(4, result.size());
        // Should not attempt to call agent proxy
        verify(restTemplate, never()).exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class));
    }

    private SessionLog createSessionLog(Long id, String username) {
        SessionLog session = new SessionLog();
        session.setId(id);
        session.setUsername(username);
        session.setSessionTm(new Timestamp(System.currentTimeMillis()));
        return session;
    }

    private Map<String, Object> createAgentSessionData(String sessionId, Long durationMinutes) {
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("sessionId", sessionId);
        sessionData.put("durationMinutes", durationMinutes);
        sessionData.put("sessionType", "agent");
        return sessionData;
    }
}