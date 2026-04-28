package com.hubilon.auth.example;

import com.hubilon.auth.KeycloakClient;
import com.hubilon.auth.TokenResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

/**
 * Authorization Code Flow 컨트롤러 예시.
 *
 * 이 파일을 각 서비스에 복사하여 사용하세요.
 * 패키지명은 각 서비스에 맞게 변경하고, @RequiredArgsConstructor 등 원하는 방식으로 주입하세요.
 *
 * application.yml 필수 설정:
 * <pre>
 * keycloak:
 *   server-url: http://keycloak-server:8080
 *   realm: my-realm
 *   client-id: my-service
 *   client-secret: xxxxxxxx
 *   redirect-uri: http://my-service/auth/callback
 *   post-logout-redirect-uri: http://my-service/auth/login
 *   permit-all-paths:
 *     - /auth/login
 *     - /auth/callback
 * </pre>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String SESSION_STATE_KEY = "oauth_state";
    private static final String SESSION_ID_TOKEN_KEY = "id_token";

    private final KeycloakClient keycloakClient;

    public AuthController(KeycloakClient keycloakClient) {
        this.keycloakClient = keycloakClient;
    }

    /**
     * [STEP 1] 로그인 시작 - Keycloak 로그인 페이지로 리다이렉트.
     *
     * 프론트엔드에서 로그인 버튼 클릭 시 이 URL로 이동시키면 됩니다.
     * GET /auth/login
     */
    @GetMapping("/login")
    public void login(HttpSession session, HttpServletResponse response) throws IOException {
        String state = UUID.randomUUID().toString();
        session.setAttribute(SESSION_STATE_KEY, state);
        response.sendRedirect(keycloakClient.getAuthorizationUrl(state));
    }

    /**
     * [STEP 2] Keycloak 로그인 완료 후 콜백 처리.
     *
     * Keycloak이 로그인 완료 후 이 URL로 code와 state를 전달합니다.
     * GET /auth/callback?code=xxx&state=yyy
     *
     * 반환된 토큰을 프론트엔드에 전달하는 방법은 서비스 정책에 따라 결정하세요:
     * - 쿠키에 저장 (HttpOnly, Secure 권장)
     * - 응답 바디로 전달 후 프론트엔드에서 저장
     * - 세션에 저장 (서버 사이드 렌더링)
     */
    @GetMapping("/callback")
    public ResponseEntity<TokenResponse> callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpSession session) {

        // CSRF 방지: 세션에 저장된 state와 비교
        String savedState = (String) session.getAttribute(SESSION_STATE_KEY);
        session.removeAttribute(SESSION_STATE_KEY);

        if (savedState == null || !savedState.equals(state)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid state parameter");
        }

        TokenResponse tokens = keycloakClient.handleCallback(code);

        // 로그아웃 시 id_token_hint로 사용하기 위해 세션에 저장
        if (tokens.getIdToken() != null) {
            session.setAttribute(SESSION_ID_TOKEN_KEY, tokens.getIdToken());
        }

        return ResponseEntity.ok(tokens);
    }

    /**
     * [STEP 3] 로그아웃 - Keycloak SSO 세션까지 완전 종료.
     *
     * Keycloak 로그아웃 페이지로 리다이렉트 → SSO 세션 만료 → post-logout-redirect-uri로 이동.
     * POST /auth/logout
     */
    @PostMapping("/logout")
    public void logout(HttpSession session, HttpServletResponse response) throws IOException {
        String idToken = (String) session.getAttribute(SESSION_ID_TOKEN_KEY);
        session.invalidate();

        String logoutUrl = keycloakClient.getLogoutUrl(idToken != null ? idToken : "");
        response.sendRedirect(logoutUrl);
    }

    /**
     * Access Token 재발급.
     * POST /auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(keycloakClient.refreshToken(request.refreshToken()));
    }

    public record RefreshRequest(String refreshToken) {}
}
