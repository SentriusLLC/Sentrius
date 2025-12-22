package io.sentrius.sso.core.services.documents.retrieval;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates URLs to prevent Server-Side Request Forgery (SSRF) attacks.
 * Blocks access to private networks, localhost, and non-HTTP(S) protocols.
 */
@Slf4j
public class UrlValidator {

    /**
     * Validates a URL to prevent SSRF attacks
     *
     * @param url The URL to validate
     * @throws DocumentRetrievalException if URL is invalid or potentially dangerous
     */
    public static void validateUrl(String url) throws DocumentRetrievalException {
        if (url == null || url.trim().isEmpty()) {
            throw new DocumentRetrievalException("URL cannot be null or empty");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new DocumentRetrievalException("Invalid URL format: " + e.getMessage());
        }

        // Validate scheme - only allow http and https
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new DocumentRetrievalException(
                "Invalid URL scheme. Only HTTP and HTTPS protocols are allowed. Found: " + scheme);
        }

        // Get the host from the URI
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new DocumentRetrievalException("URL must contain a valid host");
        }

        // Normalize host to lowercase for comparison
        host = host.toLowerCase();

        // Block localhost and localhost-like hostnames
        if (isLocalhost(host)) {
            throw new DocumentRetrievalException(
                "Access to localhost is not allowed for security reasons");
        }

        // Check if host is an IP address literal
        if (isIpAddressLiteral(host)) {
            // If it's an IP address, validate it directly
            try {
                InetAddress address = InetAddress.getByName(host);
                if (isPrivateOrReservedAddress(address)) {
                    throw new DocumentRetrievalException(
                        "Access to private or reserved IP addresses is not allowed for security reasons: " +
                            address.getHostAddress());
                }
            } catch (UnknownHostException e) {
                throw new DocumentRetrievalException("Invalid IP address: " + host);
            }
        } else {
            // For domain names, try to resolve but don't fail if DNS is unavailable
            // This allows the service to attempt the connection, where the actual HTTP client
            // will handle DNS resolution failures appropriately
            try {
                InetAddress address = InetAddress.getByName(host);
                if (isPrivateOrReservedAddress(address)) {
                    throw new DocumentRetrievalException(
                        "Access to private or reserved IP addresses is not allowed for security reasons: " +
                            address.getHostAddress());
                }
            } catch (UnknownHostException e) {
                // For domain names that don't resolve (e.g., in test environments),
                // we allow the request to proceed and let the HTTP client handle it
                log.debug("Could not pre-resolve host {}, will allow HTTP client to handle: {}",
                    host, e.getMessage());
            }
        }

        log.debug("URL validation passed for: {}", url);
    }

    /**
     * Checks if the string is an IP address literal (IPv4 or IPv6)
     */
    private static boolean isIpAddressLiteral(String host) {
        // Check for IPv4 pattern with valid octet ranges (0-255)
        if (host.matches("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$")) {
            return true;
        }
        // Check for IPv6 (contains colons)
        if (host.contains(":")) {
            return true;
        }
        return false;
    }

    /**
     * Checks if the hostname is localhost or a localhost variant
     */
    private static boolean isLocalhost(String host) {
        return host.equals("localhost") ||
            host.equals("127.0.0.1") ||
            host.equals("::1") ||
            host.equals("0.0.0.0") ||
            host.startsWith("localhost.") ||
            host.endsWith(".localhost");
    }

    /**
     * Checks if an IP address is private, loopback, link-local, or reserved
     */
    private static boolean isPrivateOrReservedAddress(InetAddress address) {
        // Check for loopback addresses (127.0.0.0/8, ::1)
        if (address.isLoopbackAddress()) {
            return true;
        }

        // Check for link-local addresses (169.254.0.0/16, fe80::/10)
        if (address.isLinkLocalAddress()) {
            return true;
        }

        // Check for site-local addresses (deprecated, but still blocked)
        // This covers 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, fec0::/10
        if (address.isSiteLocalAddress()) {
            return true;
        }

        // Check for multicast addresses
        if (address.isMulticastAddress()) {
            return true;
        }

        // Check for any local address (0.0.0.0, ::)
        if (address.isAnyLocalAddress()) {
            return true;
        }

        // Additional check for IPv4 private ranges that might not be caught
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            // Check 10.0.0.0/8
            if (bytes[0] == 10) {
                return true;
            }
            // Check 172.16.0.0/12
            if (bytes[0] == (byte) 172 && (bytes[1] & 0xF0) == 0x10) {
                return true;
            }
            // Check 192.168.0.0/16
            if (bytes[0] == (byte) 192 && bytes[1] == (byte) 168) {
                return true;
            }
            // Check 169.254.0.0/16 (AWS/Azure metadata service)
            if (bytes[0] == (byte) 169 && bytes[1] == (byte) 254) {
                return true;
            }
            // Check 127.0.0.0/8 (loopback)
            if (bytes[0] == 127) {
                return true;
            }
            // Check 0.0.0.0/8
            if (bytes[0] == 0) {
                return true;
            }
        }

        return false;
    }
}