package io.sentrius.sso.config;

import com.surrealdb.Surreal;
import com.surrealdb.signin.Root;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.services.documents.KnowledgeGraphService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SurrealDB knowledge graph database connection.
 * SurrealDB is used for storing and querying document relationships and knowledge graphs.
 *
 * This provides a SurrealDBConnectionFactory that creates fresh connections on demand,
 * allowing dynamic configuration changes via SystemOptions to take effect without restart.
 *
 * This configuration is only loaded when:
 * 1. surrealdb.enabled=true property is set (or not set, defaults to true)
 * 2. The SurrealDB classes are available on the classpath
 */
@Configuration
@ConditionalOnProperty(name = "surrealdb.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(name = "com.surrealdb.Surreal")
@Slf4j
public class SurrealDBConfig {
    
    
    @Value("${surrealdb.password:${SURREALDB_PASSWORD:}}")
    private String surrealdbPassword;
    
    /**
     * Creates a SurrealDBConnectionFactory bean that provides fresh connections.
     * This allows SystemOptions changes to take effect without requiring a restart.
     */
    @Bean
    public SurrealDBConnectionFactory surrealDBConnectionFactory(SystemOptions systemOptions) {
        return new SurrealDBConnectionFactory(systemOptions, surrealdbPassword);
    }

    /**
     * Factory class for creating SurrealDB connections.
     * Implements SurrealDBConnectionProvider for use by KnowledgeGraphService.
     * Each call to getConnection() creates a fresh connection using current SystemOptions values.
     */
    public static class SurrealDBConnectionFactory implements KnowledgeGraphService.SurrealDBConnectionProvider {

        private final SystemOptions systemOptions;
        private final String password;

        public SurrealDBConnectionFactory(SystemOptions systemOptions, String password) {
            this.systemOptions = systemOptions;
            this.password = password;
            log.info("SurrealDBConnectionFactory initialized");
        }

        /**
         * Check if SurrealDB is enabled and properly configured.
         */
        @Override
        public boolean isEnabled() {
            return systemOptions.getSurrealdbEnabled() &&
                   password != null &&
                   !password.trim().isEmpty();
        }

        /**
         * Creates a new SurrealDB connection using current SystemOptions values.
         * Caller is responsible for closing the connection when done.
         *
         * @return A new connected Surreal instance, or null if connection fails
         */
        @Override
        public Surreal getConnection() {
            if (!systemOptions.getSurrealdbEnabled()) {
                log.debug("SurrealDB is disabled via SystemOptions");
                return null;
            }

            if (password == null || password.trim().isEmpty()) {
                log.error("SurrealDB password not set. Please set SURREALDB_PASSWORD environment variable.");
                return null;
            }

            String host = systemOptions.getSurrealdbHost();
            Integer port = systemOptions.getSurrealdbPort();
            String username = systemOptions.getSurrealdbUsername();
            String namespace = systemOptions.getSurrealdbNamespace();
            String database = systemOptions.getSurrealdbDatabase();
            
            try {
                log.debug("Creating new SurrealDB connection to {}:{}", host, port);

                Surreal db = new Surreal();
                String connectionString = String.format("ws://%s:%d", host, port);
                db.connect(connectionString);

                // Sign in with root credentials
                db.signin(new Root(username, password));

                // Use specific namespace and database
                db.useNs(namespace);
                db.useDb(database);

                log.debug("Successfully connected to SurrealDB namespace: {}, database: {}",
                    namespace, database);

                return db;
            } catch (Exception e) {
                log.error("Failed to connect to SurrealDB at {}:{}: {}", host, port, e.getMessage());
                return null;
            }
        }

        /**
         * Execute a query with automatic connection management.
         * Creates a connection, executes the query, and ensures cleanup.
         *
         * @param queryExecutor Function that takes a Surreal connection and returns a result
         * @return The result of the query, or null if connection failed
         */
        public <T> T executeWithConnection(SurrealQueryExecutor<T> queryExecutor) {
            Surreal db = null;
            try {
                db = getConnection();
                if (db == null) {
                    return null;
                }
                return queryExecutor.execute(db);
            } catch (Exception e) {
                log.error("Error executing SurrealDB query: {}", e.getMessage(), e);
                return null;
            } finally {
                if (db != null) {
                    try {
                        db.close();
                    } catch (Exception e) {
                        log.warn("Error closing SurrealDB connection: {}", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Functional interface for executing queries with a Surreal connection.
     */
    @FunctionalInterface
    public interface SurrealQueryExecutor<T> {
        T execute(Surreal db) throws Exception;
    }
}
