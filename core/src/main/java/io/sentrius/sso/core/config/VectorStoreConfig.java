package io.sentrius.sso.core.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;


/**
 * Configuration for vector store capabilities in agent memory store.
 * Configuration values are read from application properties but can be 
 * overridden dynamically through SystemOptions where available.
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    @Value("${sentrius.memory.vector.dimension:1536}")
    private int vectorDimension;

    @Value("${sentrius.memory.vector.similarity-threshold:0.7}")
    private double defaultSimilarityThreshold;

    @Value("${sentrius.memory.vector.enabled:true}")
    private boolean vectorStoreEnabled;

    /**
     * Configuration properties for vector store
     */
    @Bean
    public VectorStoreProperties vectorStoreProperties() {
        VectorStoreProperties properties = new VectorStoreProperties();
        properties.setDimension(vectorDimension);
        properties.setDefaultSimilarityThreshold(defaultSimilarityThreshold);
        properties.setEnabled(vectorStoreEnabled);
        
        log.info("Vector store configuration: enabled={}, dimension={}, threshold={}", 
                properties.isEnabled(), properties.getDimension(), properties.getDefaultSimilarityThreshold());
        
        return properties;
    }

    /**
     * RestTemplate for HTTP calls with PATCH support.
     * Uses Apache HttpClient 5.x which properly supports all HTTP methods including PATCH.
     */
    @Bean
    public RestTemplate restTemplate() {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(10000); // 10 seconds
        return new RestTemplate(requestFactory);
    }

    /**
     * Properties class for vector store configuration
     */
    public static class VectorStoreProperties {
        private int dimension;
        private double defaultSimilarityThreshold;
        private boolean enabled;

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public double getDefaultSimilarityThreshold() {
            return defaultSimilarityThreshold;
        }

        public void setDefaultSimilarityThreshold(double defaultSimilarityThreshold) {
            this.defaultSimilarityThreshold = defaultSimilarityThreshold;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}