package io.dataguardians.config.security.zt;

public interface JITConfigProvider {

    Integer getMaxJitUses();

    Integer getMaxJitDurationMs();
    
    Integer getApprovedJITPeriod();

    boolean getJitRequiresTicket();

    public class JITConfigProviderFactory {

        private static final JITConfigProvider configProvider;

        static {
            // You can conditionally select the provider (e.g., based on environment, configuration, etc.)
            String providerType = System.getenv("JIT_CONFIG_CLASS");

            if (null == providerType) {
                providerType = System.getProperty("JIT_CONFIG_CLASS");
            }

            if (null != providerType && !providerType.isEmpty()) {
                try {
                    configProvider = (JITConfigProvider) Class.forName(providerType).newInstance();
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    throw new RuntimeException("Failed to instantiate JITConfigProvider", e);
                }
            } else {
                // Default to in-memory configuration
                configProvider = new DefaultJITConfigProvider();
            }
        }

        public static JITConfigProvider getConfigProvider() {
            return configProvider;
        }

    }
}
