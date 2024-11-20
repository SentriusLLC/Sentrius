package io.dataguardians.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey implements TokenProvider {

    private String apiKey;
    @Builder.Default
    private String principal = "user";

    @Override
    public String getPrincipal() {
        return principal;
    }

    @Override
    public String getToken() {
        return apiKey;
    }

    public static class ApiKeyBuilder {

        public ApiKeyBuilder fromEnv(final String apiKeyEnvName) {
            this.apiKey = System.getenv(apiKeyEnvName);
            return this;
        }

    }
}
