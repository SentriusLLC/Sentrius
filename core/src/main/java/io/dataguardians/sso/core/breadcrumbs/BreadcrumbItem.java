package io.dataguardians.sso.core.breadcrumbs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BreadcrumbItem {
    private String name;
    private String url;
    private String arguments;

    // Getters and setters
}