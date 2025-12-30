package io.sentrius.sso.core.data.attributes;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;

/**
 * Configuration for a UI resource including access requirements
 */
@Data
@Getter
@ToString
public class UIResourceConfig {
    String requiredAccess;  // Access set requirement
    String abacResource;    // ABAC resource identifier
    String uiMapping;

    UIResourceConfig(String requiredAccess, String abacResource, String uiMapping) {
        this.requiredAccess = requiredAccess;
        this.abacResource = abacResource;
        this.uiMapping = uiMapping;
    }
}
