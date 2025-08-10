package io.sentrius.sso.sshproxy.service;

import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.hostgroup.HostGroup;
import io.sentrius.sso.core.repository.SystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HostSystemSelectionServiceTest {

    @Mock
    private SystemRepository systemRepository;

    @InjectMocks
    private HostSystemSelectionService hostSystemSelectionService;

    private HostSystem validHostSystem;
    private HostSystem invalidHostSystem;

    @BeforeEach
    void setUp() {
        validHostSystem = new HostSystem();
        validHostSystem.setId(1L);
        validHostSystem.setDisplayName("Valid Host");
        validHostSystem.setHost("192.168.1.100");
        validHostSystem.setPort(22);
        validHostSystem.setSshUser("testuser");
        validHostSystem.setHostGroups(new ArrayList<>());

        invalidHostSystem = new HostSystem();
        invalidHostSystem.setId(2L);
        invalidHostSystem.setDisplayName("Invalid Host");
        // Missing required fields to make it invalid
    }

    @Test
    void testGetHostSystemById_Success() {
        when(systemRepository.findById(1L)).thenReturn(Optional.of(validHostSystem));

        Optional<HostSystem> result = hostSystemSelectionService.getHostSystemById(1L);

        assertTrue(result.isPresent());
        assertEquals(validHostSystem, result.get());
        verify(systemRepository).findById(1L);
    }

    @Test
    void testGetHostSystemById_NotFound() {
        when(systemRepository.findById(1L)).thenReturn(Optional.empty());

        Optional<HostSystem> result = hostSystemSelectionService.getHostSystemById(1L);

        assertFalse(result.isPresent());
        verify(systemRepository).findById(1L);
    }

    @Test
    void testGetHostSystemById_Exception() {
        when(systemRepository.findById(1L)).thenThrow(new RuntimeException("Database error"));

        Optional<HostSystem> result = hostSystemSelectionService.getHostSystemById(1L);

        assertFalse(result.isPresent());
        verify(systemRepository).findById(1L);
    }

    @Test
    void testGetAllHostSystems_Success() {
        List<HostSystem> hostSystems = Arrays.asList(validHostSystem, invalidHostSystem);
        when(systemRepository.findAll()).thenReturn(hostSystems);

        List<HostSystem> result = hostSystemSelectionService.getAllHostSystems();

        assertEquals(2, result.size());
        assertTrue(result.contains(validHostSystem));
        assertTrue(result.contains(invalidHostSystem));
        verify(systemRepository).findAll();
    }

    @Test
    void testGetAllHostSystems_Exception() {
        when(systemRepository.findAll()).thenThrow(new RuntimeException("Database error"));

        List<HostSystem> result = hostSystemSelectionService.getAllHostSystems();

        assertTrue(result.isEmpty());
        verify(systemRepository).findAll();
    }

    @Test
    void testGetHostSystemsByDisplayName_Success() {
        List<HostSystem> expectedSystems = Arrays.asList(validHostSystem);
        when(systemRepository.findByDisplayName("Valid Host")).thenReturn(expectedSystems);

        List<HostSystem> result = hostSystemSelectionService.getHostSystemsByDisplayName("Valid Host");

        assertEquals(1, result.size());
        assertEquals(validHostSystem, result.get(0));
        verify(systemRepository).findByDisplayName("Valid Host");
    }

    @Test
    void testGetHostSystemsByDisplayName_Exception() {
        when(systemRepository.findByDisplayName("Valid Host"))
            .thenThrow(new RuntimeException("Database error"));

        List<HostSystem> result = hostSystemSelectionService.getHostSystemsByDisplayName("Valid Host");

        assertTrue(result.isEmpty());
        verify(systemRepository).findByDisplayName("Valid Host");
    }

    @Test
    void testGetHostSystemsByHost_Success() {
        List<HostSystem> allSystems = Arrays.asList(validHostSystem, invalidHostSystem);
        when(systemRepository.findAll()).thenReturn(allSystems);

        List<HostSystem> result = hostSystemSelectionService.getHostSystemsByHost("192.168.1.100");

        assertEquals(1, result.size());
        assertEquals(validHostSystem, result.get(0));
        verify(systemRepository).findAll();
    }

    @Test
    void testGetHostSystemsByHost_NoMatch() {
        List<HostSystem> allSystems = Arrays.asList(validHostSystem);
        when(systemRepository.findAll()).thenReturn(allSystems);

        List<HostSystem> result = hostSystemSelectionService.getHostSystemsByHost("10.0.0.1");

        assertTrue(result.isEmpty());
        verify(systemRepository).findAll();
    }

    @Test
    void testGetDefaultHostSystem_Success() {
        HostGroup hostGroup = new HostGroup();
        hostGroup.setId(1L);
        validHostSystem.setHostGroups(Arrays.asList(hostGroup));
        
        List<HostSystem> hostSystems = Arrays.asList(validHostSystem);
        when(systemRepository.findAll()).thenReturn(hostSystems);

        Optional<HostSystem> result = hostSystemSelectionService.getDefaultHostSystem();

        assertTrue(result.isPresent());
        assertEquals(validHostSystem, result.get());
        verify(systemRepository).findAll();
    }

    @Test
    void testGetDefaultHostSystem_NoHostSystems() {
        when(systemRepository.findAll()).thenReturn(Arrays.asList());

        Optional<HostSystem> result = hostSystemSelectionService.getDefaultHostSystem();

        assertFalse(result.isPresent());
        verify(systemRepository).findAll();
    }

    @Test
    void testGetDefaultHostSystem_Exception() {
        when(systemRepository.findAll()).thenThrow(new RuntimeException("Database error"));

        Optional<HostSystem> result = hostSystemSelectionService.getDefaultHostSystem();

        assertFalse(result.isPresent());
        verify(systemRepository).findAll();
    }

    @Test
    void testIsHostSystemValid_ValidSystem() {
        boolean result = hostSystemSelectionService.isHostSystemValid(validHostSystem);

        assertTrue(result);
    }

    @Test
    void testIsHostSystemValid_NullSystem() {
        boolean result = hostSystemSelectionService.isHostSystemValid(null);

        assertFalse(result);
    }

    @Test
    void testIsHostSystemValid_MissingHost() {
        validHostSystem.setHost(null);

        boolean result = hostSystemSelectionService.isHostSystemValid(validHostSystem);

        assertFalse(result);
    }

    @Test
    void testIsHostSystemValid_EmptyHost() {
        validHostSystem.setHost("");

        boolean result = hostSystemSelectionService.isHostSystemValid(validHostSystem);

        assertFalse(result);
    }

    @Test
    void testIsHostSystemValid_NullPort() {
        validHostSystem.setPort(null);

        boolean result = hostSystemSelectionService.isHostSystemValid(validHostSystem);

        assertFalse(result);
    }

    @Test
    void testIsHostSystemValid_InvalidPort() {
        validHostSystem.setPort(0);

        boolean result = hostSystemSelectionService.isHostSystemValid(validHostSystem);

        assertFalse(result);
    }

    @Test
    void testIsHostSystemValid_MissingSshUser() {
        validHostSystem.setSshUser(null);

        boolean result = hostSystemSelectionService.isHostSystemValid(validHostSystem);

        assertFalse(result);
    }

    @Test
    void testIsHostSystemValid_EmptySshUser() {
        validHostSystem.setSshUser("   ");

        boolean result = hostSystemSelectionService.isHostSystemValid(validHostSystem);

        assertFalse(result);
    }
}