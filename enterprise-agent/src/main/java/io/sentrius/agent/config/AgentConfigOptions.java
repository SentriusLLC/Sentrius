package io.sentrius.agent.config;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Builder
@Slf4j
@ConfigurationProperties(prefix = "agent")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AgentConfigOptions {


    private String namePrefix;
    private String clientId;
    private String type;
    private List<String> endpoints;
    
    /**
     * Sleep duration in milliseconds when autonomous agent plan completes before restarting.
     * Defaults to 30000 (30 seconds) if not specified.
     */
    private Long idleSleepMs;

    private Ai ai;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Ai {
        private Policy policy;
        private Context context;

        @Getter
        @Setter
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Policy {
            private String id;
        }

        @Getter
        @Setter
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Context {
            private Db db;

            @Getter
            @Setter
            @AllArgsConstructor
            @NoArgsConstructor
            public static class Db {
                private String id;
            }
        }
    }
}
