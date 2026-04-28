package com.hubilon.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servlet filter that validates the Bearer JWT on every request.
 * On success, populates {@link UserContext} for the duration of the request.
 * On failure, returns 401 immediately.
 * Paths listed in {@code keycloak.permit-all-paths} bypass this filter entirely.
 */
public class KeycloakTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtDecoder jwtDecoder;
    private final KeycloakProperties properties;

    public KeycloakTokenFilter(JwtDecoder jwtDecoder, KeycloakProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.getPermitAllPaths().stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (!StringUtils.hasText(token)) {
            sendUnauthorized(response, "Authorization header is missing or not a Bearer token");
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            UserContext.set(buildUserInfo(jwt));
            chain.doFilter(request, response);
        } catch (JwtException e) {
            sendUnauthorized(response, "Token is invalid or expired");
        } finally {
            UserContext.clear();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private UserInfo buildUserInfo(Jwt jwt) {
        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        List<String> roles = new ArrayList<>();

        // Realm-level roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map && realmAccess.get("roles") instanceof List<?> realmRoles) {
            realmRoles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(roles::add);
        }

        // Client-level roles
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof Map) {
            Object clientAccess = resourceAccess.get(properties.getClientId());
            if (clientAccess instanceof Map<?, ?> clientMap
                    && clientMap.get("roles") instanceof List<?> clientRoles) {
                clientRoles.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .forEach(roles::add);
            }
        }

        return new UserInfo(userId, username, email, roles);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
