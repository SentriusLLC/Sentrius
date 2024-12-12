package io.dataguardians.sso.security;

/**
 * Provides a principal and token for
 */
public interface TokenProvider {

    String getPrincipal();

    String getToken();
}
