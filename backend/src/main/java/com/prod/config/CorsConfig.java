package com.prod.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS (Cross-Origin Resource Sharing) configuration for frontend access.
 *
 * This class configures CORS policies to allow the frontend application
 * to communicate with the backend API across different origins. It is essential
 * for development scenarios where the frontend runs on a different port/domain
 * than the backend, and for production deployments with separate frontend/backend.
 *
 * Features:
 * - Configurable allowed origins from application.yml
 * - Credentials support (cookies, authorization headers)
 * - Common HTTP methods allowed (GET, POST, PUT, DELETE, PATCH, OPTIONS)
 * - Standard headers allowed (Content-Type, Authorization, Accept)
 * - Configurable max-age for preflight requests caching
 *
 * Security Considerations:
 * - In production, CORS origins should be explicitly configured
 * - Avoid using wildcard (*) when credentials are enabled
 * - Preflight OPTIONS requests are handled automatically
 * - CORS validation occurs before Spring Security filters
 *
 * The configuration uses a CorsFilter bean which integrates with Spring's
 * filter chain, ensuring CORS headers are applied consistently across all
 * endpoints.
 *
 * @see org.springframework.web.filter.CorsFilter
 * @see org.springframework.web.cors.CorsConfiguration
 */
@Configuration
@Slf4j
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private List<String> allowedMethods;

    @Value("${app.cors.allowed-headers:Authorization,Content-Type,Accept,Origin,Access-Control-Request-Method,Access-Control-Request-Headers}")
    private List<String> allowedHeaders;

    @Value("${app.cors.exposed-headers:Authorization,Content-Type,Accept}")
    private List<String> exposedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    /**
     * Configure CORS filter for cross-origin requests.
     *
     * This method creates a CorsFilter bean that applies CORS configuration
     * to all HTTP requests. The configuration:
     * - Allows specified origins (configurable via application.yml)
     * - Enables credentials (cookies, authorization headers)
     * - Allows common HTTP methods for REST API
     * - Sets appropriate headers for frontend integration
     * - Configures preflight request caching
     *
     * Origins Configuration:
     * - Development: http://localhost:3000 (React), http://localhost:5173 (Vite)
     * - Production: Configure via app.cors.allowed-origins environment variable
     *
     * When credentials are enabled, allowedOrigins cannot be "*" (wildcard).
     * Explicit origins must be configured to maintain security.
     *
     * @return configured CorsFilter for CORS request handling
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Configure allowed origins
        config.setAllowedOrigins(allowedOrigins);
        log.info("CORS allowed origins: {}", allowedOrigins);

        // Configure allowed HTTP methods
        config.setAllowedMethods(allowedMethods);
        log.debug("CORS allowed methods: {}", allowedMethods);

        // Configure allowed headers
        config.setAllowedHeaders(allowedHeaders);
        log.debug("CORS allowed headers: {}", allowedHeaders);

        // Configure exposed headers (headers visible to frontend)
        config.setExposedHeaders(exposedHeaders);
        log.debug("CORS exposed headers: {}", exposedHeaders);

        // Enable credentials (cookies, authorization headers)
        config.setAllowCredentials(allowCredentials);
        log.info("CORS allow credentials: {}", allowCredentials);

        // Configure max-age for preflight request caching (in seconds)
        config.setMaxAge(maxAge);
        log.debug("CORS max-age: {} seconds", maxAge);

        // Apply CORS configuration to all paths
        source.registerCorsConfiguration("/**", config);

        log.info("CorsFilter configured successfully");
        return new CorsFilter(source);
    }

    /**
     * Get the list of allowed origins.
     *
     * This method is primarily useful for debugging and logging purposes.
     * It returns the currently configured allowed origins which can be
     * used to verify CORS configuration at runtime.
     *
     * @return list of allowed origin URLs
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Get the list of allowed HTTP methods.
     *
     * This method is primarily useful for debugging and logging purposes.
     * It returns the currently configured allowed methods which can be
     * used to verify CORS configuration at runtime.
     *
     * @return list of allowed HTTP methods
     */
    public List<String> getAllowedMethods() {
        return allowedMethods;
    }
}
