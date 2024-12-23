package io.sentrius.sso.core.model;

/**
 * Interface for a host.
 */
public interface Host {

    /**
     * unique identifier for host
     * @return
     */
    Long getId();

    /**
     * The name of the host.
     * @return The name of the host.
     */
    String getHost();
}
