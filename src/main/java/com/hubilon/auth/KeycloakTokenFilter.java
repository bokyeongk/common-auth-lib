package com.hubilon.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 모든 요청에서 JWT를 검증하는 필터.
 *
 * <p>토큰 추출 우선순위:
 * <ol>
 *   <li>Authorization: Bearer {token} 헤더 (API 클라이언트 / 모바일)</li>
 *   <li>HttpOnly 쿠키 {@code access_token} (브라우저 클라이언트)</li>
 * </ol>
 *
 * 검증 성공 시 {@link UserContext}에 유저 정보를 저장하고 요청을 통과시킵니다.
 * 검증 실패 시 401을 즉시 반환합니다.
 * {@code keycloak.permit-all-paths}에 등록된 경로는 이 필터를 건너뜁니다.
 */
public class KeycloakTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<String> KNOWN_CLAIMS = Set.of(
            "sub", "preferred_username", "email",
            "realm_access", "resource_access",
            "iss", "aud", "exp", "iat", "jti",
            "typ", "azp", "sid", "nonce", "auth_time",
            "acr", "session_state", "at_hash", "c_hash"
    );
    private final JwtDecoder jwtDecoder;
    private final KeycloakProperties properties;

    public KeycloakTokenFilter(JwtDecoder jwtDecoder, KeycloakProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        KeycloakProperties.Uri uri = properties.getUri();
        return List.of(uri.getLogin(), uri.getCallback(), uri.getLogout(), uri.getRefresh())
                       .stream().anyMatch(p -> PATH_MATCHER.match(p, path))
                || properties.getPermitAllPaths().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);

        if (!StringUtils.hasText(token)) {
            sendUnauthorized(response, "Token is missing");
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            UserInfo userInfo = buildUserInfo(jwt);
            UserContext.set(userInfo);

            List<SimpleGrantedAuthority> authorities = userInfo.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));

            chain.doFilter(request, response);
        } catch (JwtException e) {
            sendUnauthorized(response, "Token is invalid or expired");
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 1순위: Authorization 헤더 (Bearer) → 2순위: HttpOnly 쿠키
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (KeycloakProperties.ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private UserInfo buildUserInfo(Jwt jwt) {
        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        List<String> roles = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map && realmAccess.get("roles") instanceof List<?> realmRoles) {
            realmRoles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(roles::add);
        }

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

        Map<String, Object> attributes = jwt.getClaims().entrySet().stream()
                .filter(e -> !KNOWN_CLAIMS.contains(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));

        return new UserInfo(userId, username, email, roles, attributes);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
