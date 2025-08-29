package io.sentrius.sso.core.dto.agents;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.domain.Sort;

import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryQueryDTO {

    private String agentId;
    private String classification;
    private String markings;
    private String accessLevel;
    private String creatorUserId;
    private String memoryKey;
    private String searchTerm;
    private Boolean includeExpired;
    private List<String> includeMarkings;
    private List<String> excludeMarkings;
    private String memoryType;
    
    // Pagination parameters
    private Integer page;
    private Integer size;
    private String sortBy;
    private Sort.Direction sortDirection;
    
    // Response filtering
    private Boolean includeMetadata;
    private Boolean includeSharedAgents;
    private Boolean excludeValues; // Only return metadata, not actual values

    // Default values
    public Integer getPage() {
        return page != null ? page : 0;
    }

    public Integer getSize() {
        return size != null ? size : 20;
    }

    public String getSortBy() {
        return sortBy != null ? sortBy : "createdAt";
    }

    public Sort.Direction getSortDirection() {
        return sortDirection != null ? sortDirection : Sort.Direction.DESC;
    }

    public Boolean getIncludeExpired() {
        return includeExpired != null ? includeExpired : false;
    }

    public Boolean getIncludeMetadata() {
        return includeMetadata != null ? includeMetadata : true;
    }

    public Boolean getIncludeSharedAgents() {
        return includeSharedAgents != null ? includeSharedAgents : true;
    }

    public Boolean getExcludeValues() {
        return excludeValues != null ? excludeValues : false;
    }

    // Helper methods for validation
    public boolean isValid() {
        return getPage() >= 0 && getSize() > 0 && getSize() <= 100;
    }

    public boolean hasFilters() {
        return agentId != null || classification != null || markings != null || 
               accessLevel != null || creatorUserId != null || memoryKey != null ||
               searchTerm != null || (includeMarkings != null && !includeMarkings.isEmpty()) ||
               (excludeMarkings != null && !excludeMarkings.isEmpty());
    }
}