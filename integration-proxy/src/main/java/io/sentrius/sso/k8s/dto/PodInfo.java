package io.sentrius.sso.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO for Kubernetes Pod information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PodInfo {
    private String name;
    private String namespace;
    private String phase;
    private String image;
    private List<String> images;
    private OffsetDateTime creationTimestamp;
}
