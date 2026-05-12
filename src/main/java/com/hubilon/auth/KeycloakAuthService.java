package com.hubilon.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;

public class KeycloakAuthService {

    private final KeycloakClient keycloakClient;
    private final KeycloakProperties properties;

    public KeycloakAuthService(KeycloakClient keycloakClient, KeycloakProperties properties) {
        this.keycloakClient = keycloakClient;
        this.properties = properties;
    }

    public ResponseEntity<LoginResponse> loginWithPassword(LoginRequest loginRequest,
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

        storeIdTokenInSession(request, tokens);

        setAuthCookie(response, KeycloakProperties.ACCESS_TOKEN_COOKIE,
                tokens.getAccessToken(), (int) tokens.getExpiresIn());
        setAuthCookie(response, KeycloakProperties.REFRESH_TOKEN_COOKIE,
                tokens.getRefreshToken(), (int) tokens.getRefreshExpiresIn());

        response.setHeader("X-XSRF-TOKEN", csrfToken.getToken());

        return ResponseEntity.ok(LoginResponse.from(tokens));
    }

    public ResponseEntity<Void> register(RegisterRequest registerRequest, CsrfToken csrfToken) {
        if (csrfToken == null) {
            return ResponseEntity.badRequest().build();
        }

        if (!StringUtils.hasText(registerRequest.getUsername())
                || !StringUtils.hasText(registerRequest.getPassword())
                || !StringUtils.hasText(registerRequest.getEmail())) {
            return ResponseEntity.badRequest().build();
        }

        try {
            keycloakClient.register(registerRequest);
        } catch (KeycloakClient.KeycloakUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (KeycloakClient.KeycloakConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (KeycloakClient.KeycloakAuthException e) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private void storeIdTokenInSession(HttpServletRequest request, TokenResponse tokens) {
        HttpSession session = request.getSession(true);
        if (StringUtils.hasText(tokens.getIdToken())) {
            session.setAttribute(properties.getSessionIdTokenKey(), tokens.getIdToken());
        }
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
}
