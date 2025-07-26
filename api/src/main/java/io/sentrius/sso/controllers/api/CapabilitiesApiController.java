package io.sentrius.sso.controllers.api;

import io.sentrius.sso.core.annotations.LimitAccess;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.dto.capabilities.EndpointDescriptor;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.security.enums.ApplicationAccessEnum;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.services.capabilities.EndpointScanningService;
import io.sentrius.sso.core.utils.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/v1/capabilities")
public class CapabilitiesApiController extends BaseController {

    private final EndpointScanningService endpointScanningService;
    private final RestTemplate restTemplate;
    private final String proxyUrl;
    private final Cache<String, List<EndpointDescriptor>> endpointCache;
    private final ZeroTrustClientService zeroTrustClientService;

    public CapabilitiesApiController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService,
        EndpointScanningService endpointScanningService,
        @Value("${sentrius.integration.proxyUrl:http://integration-proxy:8080/}") String proxyUrl,
        ZeroTrustClientService zeroTrustClientService
    ) {
        super(userService, systemOptions, errorOutputService);
        this.endpointScanningService = endpointScanningService;
        this.proxyUrl = proxyUrl;
        this.zeroTrustClientService = zeroTrustClientService;
        this.restTemplate = new RestTemplate();
        this.endpointCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .build();
    }

    private List<EndpointDescriptor> getAllEndpointsCached() {
        return endpointCache.get("integration-endpoints", key -> {

            try {
                var url = proxyUrl;
                if (url.endsWith("/")) {
                    url += "api/v1/capabilities/endpoints";
                } else {
                    url += "/api/v1/capabilities/endpoints";
                }
                log.info("Fetching endpoints from integration proxy: {}", url);
                var endpointStr = zeroTrustClientService.callAuthenticatedGetOnApi(proxyUrl, "/api/v1/capabilities" +
                    "/endpoints");

                Objects.requireNonNull(endpointStr);

                EndpointDescriptor[] endpoints = JsonUtil.MAPPER.readValue(endpointStr, EndpointDescriptor[].class);
                log.info("Found {} endpoints from integration proxy", endpoints.length);
                log.info("Endpoints: {}", Arrays.toString(endpoints));
                Arrays.stream(endpoints).forEach(endpoint -> {
                    endpoint.setServiceUrl(proxyUrl);
                    log.info("Found endpoint: {}", endpoint);
                });

                return endpoints != null ? List.of(endpoints) : List.of();
            } catch (Exception e) {
                log.error("Failed to fetch from integration proxy", e);
                return List.of();
            } catch (ZtatException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @GetMapping("/integrated")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<EndpointDescriptor>> getIntegratedEndpoints(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Boolean requiresAuth) {
        List<EndpointDescriptor> endpoints = endpointScanningService.getAllEndpoints();

        endpoints.addAll( getAllEndpointsCached() );


        if (type != null) {
            endpoints = endpoints.stream()
                .filter(endpoint -> type.equalsIgnoreCase(endpoint.getType()))
                .collect(Collectors.toList());
        }

        if (requiresAuth != null) {
            endpoints = endpoints.stream()
                .filter(endpoint -> endpoint.isRequiresAuthentication() == requiresAuth)
                .collect(Collectors.toList());
        }

        return ResponseEntity.ok(endpoints);
    }

    @GetMapping("/integrated/refresh")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> refreshIntegratedEndpoints() {
        log.info("Manually refreshing integration proxy endpoint cache");
        endpointCache.invalidate("integration-endpoints");
        List<EndpointDescriptor> refreshed = getAllEndpointsCached();
        return ResponseEntity.ok("Refreshed. Found " + refreshed.size() + " endpoints.");
    }

    @GetMapping("/endpoints")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<EndpointDescriptor>> getAllEndpoints(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Boolean requiresAuth,
        HttpServletRequest request,
        HttpServletResponse response) {

        log.info("Retrieving all endpoints with filters - type: {}, requiresAuth: {}", type, requiresAuth);

        List<EndpointDescriptor> endpoints = endpointScanningService.getAllEndpoints();

        endpoints.addAll( getAllEndpointsCached() );
        // Apply filters if provided
        if (type != null) {
            endpoints = endpoints.stream()
                .filter(endpoint -> type.equalsIgnoreCase(endpoint.getType()))
                .collect(Collectors.toList());
        }

        if (requiresAuth != null) {
            endpoints = endpoints.stream()
                .filter(endpoint -> endpoint.isRequiresAuthentication() == requiresAuth)
                .collect(Collectors.toList());
        }

        log.info("Returning {} endpoints", endpoints.size());
        return ResponseEntity.ok(endpoints);
    }

    /**
     * Returns only REST API endpoints.
     */
    @GetMapping("/rest")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<EndpointDescriptor>> getRestEndpoints(
        HttpServletRequest request,
        HttpServletResponse response) {

        log.info("Retrieving REST endpoints");

        List<EndpointDescriptor> endpoints = endpointScanningService.getAllEndpoints()
            .stream()
            .filter(endpoint -> "REST".equals(endpoint.getType()))
            .collect(Collectors.toList());

        log.info("Returning {} REST endpoints", endpoints.size());
        return ResponseEntity.ok(endpoints);
    }

    /**
     * Returns only Verb methods (for AI agents).
     */
    @GetMapping("/verbs")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_LOG_IN})
    public ResponseEntity<List<EndpointDescriptor>> getVerbEndpoints(
        HttpServletRequest request,
        HttpServletResponse response) {

        log.info("Retrieving Verb endpoints");

        List<EndpointDescriptor> endpoints = endpointScanningService.getAllEndpoints()
            .stream()
            .filter(endpoint -> "VERB".equals(endpoint.getType()))
            .collect(Collectors.toList());

        log.info("Returning {} Verb endpoints", endpoints.size());
        return ResponseEntity.ok(endpoints);
    }

    /**
     * Forces a refresh of the endpoint cache.
     * This can be useful during development or after deploying new capabilities.
     */
    @GetMapping("/refresh")
    @LimitAccess(applicationAccess = {ApplicationAccessEnum.CAN_MANAGE_APPLICATION})
    public ResponseEntity<String> refreshEndpoints(
        HttpServletRequest request,
        HttpServletResponse response) {

        log.info("Refreshing endpoint cache");
        endpointScanningService.refreshEndpoints();

        int count = endpointScanningService.getAllEndpoints().size();
        String message = String.format("Endpoint cache refreshed. Found %d endpoints.", count);

        log.info(message);
        return ResponseEntity.ok(message);
    }
}
