package io.dataguardians.security;

/**
 * Provides a principal and token for
 */
public interface TokenProvider {

    String getPrincipal();

    String getToken();
}
