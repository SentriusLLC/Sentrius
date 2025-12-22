package io.sentrius.sso.core.services.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.dto.agents.AgentTemplateDTO;
import io.sentrius.sso.core.model.agents.AgentTemplate;
import io.sentrius.sso.core.repository.AgentTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgentTemplateServiceTest {
    
    @Mock
    private AgentTemplateRepository templateRepository;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private AgentTemplateService service;
    
    private AgentTemplate testTemplate;
    private UUID templateId;
    
    @BeforeEach
    void setUp() {
        templateId = UUID.randomUUID();
        testTemplate = AgentTemplate.builder()
            .id(templateId)
            .name("Test Template")
            .description("Test Description")
            .agentType("test-type")
            .icon("fa-test")
            .category("Testing")
            .defaultConfiguration("{\"key\": \"value\"}")
            .systemTemplate(false)
            .enabled(true)
            .displayOrder(1)
            .createdBy("test-user")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
    
    @Test
    void testGetAllEnabledTemplates() {
        List<AgentTemplate> templates = Arrays.asList(testTemplate);
        when(templateRepository.findByEnabledTrueOrderByDisplayOrderAsc()).thenReturn(templates);
        
        List<AgentTemplateDTO> result = service.getAllEnabledTemplates();
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testTemplate.getName(), result.get(0).getName());
        assertEquals(testTemplate.getDescription(), result.get(0).getDescription());
        verify(templateRepository, times(1)).findByEnabledTrueOrderByDisplayOrderAsc();
    }
    
    @Test
    void testGetTemplateById() {
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));
        
        Optional<AgentTemplateDTO> result = service.getTemplateById(templateId);
        
        assertTrue(result.isPresent());
        assertEquals(testTemplate.getName(), result.get().getName());
        assertEquals(testTemplate.getAgentType(), result.get().getAgentType());
        verify(templateRepository, times(1)).findById(templateId);
    }
    
    @Test
    void testGetTemplateByName() {
        when(templateRepository.findByName("Test Template")).thenReturn(Optional.of(testTemplate));
        
        Optional<AgentTemplateDTO> result = service.getTemplateByName("Test Template");
        
        assertTrue(result.isPresent());
        assertEquals(testTemplate.getName(), result.get().getName());
        verify(templateRepository, times(1)).findByName("Test Template");
    }
    
    @Test
    void testCreateTemplate() {
        AgentTemplateDTO dto = AgentTemplateDTO.builder()
            .name("New Template")
            .description("New Description")
            .agentType("new-type")
            .icon("fa-new")
            .category("New")
            .defaultConfiguration("{}")
            .systemTemplate(false)
            .enabled(true)
            .displayOrder(1)
            .createdBy("test-user")
            .build();
        
        AgentTemplate savedTemplate = AgentTemplate.builder()
            .id(UUID.randomUUID())
            .name(dto.getName())
            .description(dto.getDescription())
            .agentType(dto.getAgentType())
            .icon(dto.getIcon())
            .category(dto.getCategory())
            .defaultConfiguration(dto.getDefaultConfiguration())
            .systemTemplate(dto.isSystemTemplate())
            .enabled(dto.isEnabled())
            .displayOrder(dto.getDisplayOrder())
            .createdBy(dto.getCreatedBy())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        when(templateRepository.save(any(AgentTemplate.class))).thenReturn(savedTemplate);
        
        AgentTemplateDTO result = service.createTemplate(dto);
        
        assertNotNull(result);
        assertEquals(dto.getName(), result.getName());
        assertEquals(dto.getAgentType(), result.getAgentType());
        verify(templateRepository, times(1)).save(any(AgentTemplate.class));
    }
    
    @Test
    void testUpdateTemplate() {
        AgentTemplateDTO updateDto = AgentTemplateDTO.builder()
            .name("Updated Template")
            .description("Updated Description")
            .agentType("updated-type")
            .icon("fa-updated")
            .category("Updated")
            .defaultConfiguration("{\"updated\": true}")
            .systemTemplate(false)
            .enabled(true)
            .displayOrder(2)
            .build();
        
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));
        when(templateRepository.save(any(AgentTemplate.class))).thenReturn(testTemplate);
        
        AgentTemplateDTO result = service.updateTemplate(templateId, updateDto);
        
        assertNotNull(result);
        verify(templateRepository, times(1)).findById(templateId);
        verify(templateRepository, times(1)).save(any(AgentTemplate.class));
    }
    
    @Test
    void testUpdateTemplateNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(templateRepository.findById(nonExistentId)).thenReturn(Optional.empty());
        
        AgentTemplateDTO updateDto = AgentTemplateDTO.builder()
            .name("Updated")
            .description("Updated")
            .agentType("updated")
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.updateTemplate(nonExistentId, updateDto);
        });
    }
    
    @Test
    void testUpdateSystemTemplate() {
        AgentTemplate systemTemplate = AgentTemplate.builder()
            .id(templateId)
            .name("System Template")
            .systemTemplate(true)
            .build();
        
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(systemTemplate));
        
        AgentTemplateDTO updateDto = AgentTemplateDTO.builder()
            .name("Updated")
            .build();
        
        assertThrows(IllegalStateException.class, () -> {
            service.updateTemplate(templateId, updateDto);
        });
    }
    
    @Test
    void testDeleteTemplate() {
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(testTemplate));
        doNothing().when(templateRepository).delete(testTemplate);
        
        service.deleteTemplate(templateId);
        
        verify(templateRepository, times(1)).findById(templateId);
        verify(templateRepository, times(1)).delete(testTemplate);
    }
    
    @Test
    void testDeleteSystemTemplate() {
        AgentTemplate systemTemplate = AgentTemplate.builder()
            .id(templateId)
            .name("System Template")
            .systemTemplate(true)
            .build();
        
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(systemTemplate));
        
        assertThrows(IllegalStateException.class, () -> {
            service.deleteTemplate(templateId);
        });
    }
    
    @Test
    void testGetTemplatesByCategory() {
        List<AgentTemplate> templates = Arrays.asList(testTemplate);
        when(templateRepository.findByCategoryAndEnabledTrueOrderByDisplayOrderAsc("Testing"))
            .thenReturn(templates);
        
        List<AgentTemplateDTO> result = service.getTemplatesByCategory("Testing");
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testTemplate.getCategory(), result.get(0).getCategory());
        verify(templateRepository, times(1))
            .findByCategoryAndEnabledTrueOrderByDisplayOrderAsc("Testing");
    }
}
