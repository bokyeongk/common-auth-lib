package com.hubilon.auth.example;

import com.hubilon.auth.KeycloakClient;
import com.hubilon.auth.KeycloakProperties;
import com.hubilon.auth.TokenResponse;
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
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

/**
 * Authorization Code Flow + HttpOnly 쿠키 + CSRF Token 컨트롤러 예시.
 *
 * 이 파일을 각 서비스에 복사하여 사용하세요.
 * 패키지명만 서비스에 맞게 변경하면 됩니다.
 *
 * <h3>필수 application.yml 설정</h3>
 * <pre>
 * keycloak:
 *   server-url: http://keycloak-server:8080
 *   realm: my-realm
 *   client-id: my-service
 *   client-secret: xxxxxxxx
 *   redirect-uri: http://my-service/auth/callback
 *   post-logout-redirect-uri: http://my-service/auth/login
 *   post-login-redirect-uri: /               # 로그인 후 이동할 경로
 *   secure-cookie: true                       # 로컬 HTTP 개발 시 false
 *   permit-all-paths:
 *     - /auth/login
 *     - /auth/callback
 * </pre>
 *
 * <h3>프론트엔드 연동</h3>
 * <ul>
 *   <li>POST 요청 시 {@code X-XSRF-TOKEN} 헤더에 {@code XSRF-TOKEN} 쿠키 값을 담아 전송</li>
 *   <li>토큰은 HttpOnly 쿠키로 자동 관리되므로 JS에서 직접 다루지 않습니다</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String OAUTH_STATE_COOKIE = "oauth_state";
    private static final String SESSION_ID_TOKEN_KEY = "id_token";

    private final KeycloakClient keycloakClient;
    private final KeycloakProperties properties;

    public AuthController(KeycloakClient keycloakClient, KeycloakProperties properties) {
        this.keycloakClient = keycloakClient;
        this.properties = properties;
    }

    /**
     * [STEP 1] 로그인 시작.
     * CSRF 방지용 state를 세션에 저장하고 Keycloak 로그인 페이지로 리다이렉트합니다.
     * GET /auth/login
     */
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String state = UUID.randomUUID().toString();
        // SameSite=Lax: Keycloak → 앱 cross-site GET 리다이렉트 시에도 쿠키 전송 허용
        ResponseCookie stateCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Lax")
                .path("/auth/callback")
                .maxAge(300)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie.toString());
        response.sendRedirect(keycloakClient.getAuthorizationUrl(state));
    }

    /**
     * [STEP 2] Keycloak 콜백 처리.
     * code를 토큰으로 교환하고 access_token, refresh_token을 HttpOnly 쿠키로 발급합니다.
     * GET /auth/callback?code=...&state=...
     */
    @GetMapping("/callback")
    public void callback(@RequestParam String code,
                         @RequestParam String state,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         CsrfToken csrfToken) throws IOException {

        // 세션 대신 쿠키에서 state 읽기: cross-site 리다이렉트 시 JSESSIONID 미전송 문제 방지
        String savedState = extractCookieValue(request, OAUTH_STATE_COOKIE);
        ResponseCookie clearStateCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, "")
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Lax")
                .path("/auth/callback")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearStateCookie.toString());

        if (savedState == null || !savedState.equals(state)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid state parameter");
            return;
        }

        TokenResponse tokens = keycloakClient.handleCallback(code);

        // id_token은 SSO 로그아웃(id_token_hint)에 필요하므로 서버 세션에 보관
        HttpSession session = request.getSession(true);
        if (StringUtils.hasText(tokens.getIdToken())) {
            session.setAttribute(SESSION_ID_TOKEN_KEY, tokens.getIdToken());
        }

        // access_token: API 요청마다 자동 전송 (HttpOnly, JS 접근 불가)
        setAuthCookie(response, KeycloakProperties.ACCESS_TOKEN_COOKIE,
                tokens.getAccessToken(), (int) tokens.getExpiresIn());

        // refresh_token: /auth/refresh 엔드포인트가 서버에서 직접 읽음 (HttpOnly, JS 접근 불가)
        setAuthCookie(response, KeycloakProperties.REFRESH_TOKEN_COOKIE,
                tokens.getRefreshToken(), (int) tokens.getRefreshExpiresIn());

        if (csrfToken != null) {
            response.setHeader("X-XSRF-TOKEN", csrfToken.getToken());
        }

        response.sendRedirect(properties.getPostLoginRedirectUri());
    }

    /**
     * [STEP 3] 로그아웃.
     * HttpOnly 쿠키를 삭제하고 Keycloak SSO 세션까지 만료시킵니다.
     * POST /auth/logout  (X-XSRF-TOKEN 헤더 필요)
     */
    @GetMapping("/logout")
    public void logout(HttpServletRequest request, HttpSession session, HttpServletResponse response) throws IOException {
        String idToken = (String) session.getAttribute(SESSION_ID_TOKEN_KEY);
        String refreshToken = extractCookieValue(request, KeycloakProperties.REFRESH_TOKEN_COOKIE);

        // 백채널: refresh_token으로 Keycloak 세션 즉시 종료 (브라우저 redirect 전에 보장)
        if (StringUtils.hasText(refreshToken)) {
            try {
                keycloakClient.revokeToken(refreshToken);
            } catch (Exception ignored) {
                // 이미 만료된 토큰이면 무시
            }
        }

        session.invalidate();
        clearAuthCookie(response, KeycloakProperties.ACCESS_TOKEN_COOKIE);
        clearAuthCookie(response, KeycloakProperties.REFRESH_TOKEN_COOKIE);

        response.sendRedirect(keycloakClient.getLogoutUrl(idToken));
    }

    /**
     * Access Token 재발급.
     * refresh_token HttpOnly 쿠키를 서버가 직접 읽어 새 토큰을 발급합니다.
     * POST /auth/refresh  (X-XSRF-TOKEN 헤더 필요)
     */
    @PostMapping("/refresh")
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

    // ── 쿠키 유틸 ────────────────────────────────────────────────────

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