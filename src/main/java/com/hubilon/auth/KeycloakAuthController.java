package com.hubilon.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

/**
 * Authorization Code Flow 인증 엔드포인트. {@code KeycloakAutoConfiguration}에 의해 자동 등록된다.
 *
 * <p>비활성화하고 직접 구현하려면:
 * <pre>
 * keycloak:
 *   auth-controller:
 *     enabled: false
 * </pre>
 */
@RestController
public class KeycloakAuthController {

    private final KeycloakClient keycloakClient;
    private final KeycloakProperties properties;

    public KeycloakAuthController(KeycloakClient keycloakClient, KeycloakProperties properties) {
        this.keycloakClient = keycloakClient;
        this.properties = properties;
    }

    @GetMapping("${keycloak.uri.login:/auth/login}")
    public void login(HttpServletResponse response) throws IOException {
        String state = UUID.randomUUID().toString();
        ResponseCookie stateCookie = ResponseCookie.from(properties.getOauthStateCookie(), state)
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Lax")
                .path(properties.getUri().getCallback())
                .maxAge(300)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie.toString());
        response.sendRedirect(keycloakClient.getAuthorizationUrl(state));
    }

    @GetMapping("${keycloak.uri.callback:/auth/callback}")
    public void callback(@RequestParam("code") String code,
                         @RequestParam("state") String state,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         CsrfToken csrfToken) throws IOException {

        String savedState = extractCookieValue(request, properties.getOauthStateCookie());
        ResponseCookie clearStateCookie = ResponseCookie.from(properties.getOauthStateCookie(), "")
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Lax")
                .path(properties.getUri().getCallback())
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearStateCookie.toString());

        if (savedState == null || !savedState.equals(state)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid state parameter");
            return;
        }

        TokenResponse tokens = keycloakClient.handleCallback(code);

        HttpSession session = request.getSession(true);
        if (StringUtils.hasText(tokens.getIdToken())) {
            session.setAttribute(properties.getSessionIdTokenKey(), tokens.getIdToken());
        }

        setAuthCookie(response, KeycloakProperties.ACCESS_TOKEN_COOKIE,
                tokens.getAccessToken(), (int) tokens.getExpiresIn());
        setAuthCookie(response, KeycloakProperties.REFRESH_TOKEN_COOKIE,
                tokens.getRefreshToken(), (int) tokens.getRefreshExpiresIn());

        if (csrfToken != null) {
            response.setHeader("X-XSRF-TOKEN", csrfToken.getToken());
        }

        response.sendRedirect(properties.getPostLoginRedirectUri());
    }

    @GetMapping("${keycloak.uri.logout:/auth/logout}")
    public void logout(HttpServletRequest request, HttpSession session,
                       HttpServletResponse response) throws IOException {
        String idToken = (String) session.getAttribute(properties.getSessionIdTokenKey());
        String refreshToken = extractCookieValue(request, KeycloakProperties.REFRESH_TOKEN_COOKIE);

        if (StringUtils.hasText(refreshToken)) {
            try {
                keycloakClient.revokeToken(refreshToken);
            } catch (Exception ignored) {
            }
        }

        session.invalidate();
        clearAuthCookie(response, KeycloakProperties.ACCESS_TOKEN_COOKIE);
        clearAuthCookie(response, KeycloakProperties.REFRESH_TOKEN_COOKIE);

        response.sendRedirect(keycloakClient.getLogoutUrl(idToken));
    }

    /**
     * ROPC Flow 직접 로그인. Keycloak Admin에서 Direct Access Grants Enabled 필요.
     *
     * <p>permit-all-paths에 이 경로가 등록된 경우 CSRF 필터가 우회될 수 있다.
     * csrfToken null 여부로 보호 상태를 검증한다.
     */
    @PostMapping("${keycloak.uri.login:/auth/login}")
    public ResponseEntity<LoginResponse> loginWithPassword(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            CsrfToken csrfToken) {

        if (csrfToken == null) {
            return ResponseEntity.badRequest().build();
        }

        TokenResponse tokens;
        try {
            tokens = keycloakClient.loginWithPassword(
                    loginRequest.getUsername(), loginRequest.getPassword());
        } catch (KeycloakClient.KeycloakAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        HttpSession session = request.getSession(true);
        if (StringUtils.hasText(tokens.getIdToken())) {
            session.setAttribute(properties.getSessionIdTokenKey(), tokens.getIdToken());
        }

        setAuthCookie(response, KeycloakProperties.ACCESS_TOKEN_COOKIE,
                tokens.getAccessToken(), (int) tokens.getExpiresIn());
        setAuthCookie(response, KeycloakProperties.REFRESH_TOKEN_COOKIE,
                tokens.getRefreshToken(), (int) tokens.getRefreshExpiresIn());

        response.setHeader("X-XSRF-TOKEN", csrfToken.getToken());

        return ResponseEntity.ok(LoginResponse.from(tokens));
    }

    @PostMapping("${keycloak.uri.refresh:/auth/refresh}")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookieValue(request, KeycloakProperties.REFRESH_TOKEN_COOKIE);

        if (!StringUtils.hasText(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        TokenResponse tokens = keycloakClient.refreshToken(refreshToken);

        setAuthCookie(response, KeycloakProperties.ACCESS_TOKEN_COOKIE,
                tokens.getAccessToken(), (int) tokens.getExpiresIn());
        setAuthCookie(response, KeycloakProperties.REFRESH_TOKEN_COOKIE,
                tokens.getRefreshToken(), (int) tokens.getRefreshExpiresIn());

        return ResponseEntity.noContent().build();
    }

    private void setAuthCookie(HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
