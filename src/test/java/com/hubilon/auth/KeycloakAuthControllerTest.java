package com.hubilon.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KeycloakAuthControllerTest {

    private KeycloakClient keycloakClient;
    private KeycloakProperties properties;
    private KeycloakAuthController controller;

    @BeforeEach
    void setUp() {
        keycloakClient = mock(KeycloakClient.class);
        properties = new KeycloakProperties();
        properties.setSecureCookie(false);
        properties.setPostLoginRedirectUri("/dashboard");
        KeycloakAuthService keycloakAuthService = new KeycloakAuthService(keycloakClient, properties);
        controller = new KeycloakAuthController(keycloakClient, properties, keycloakAuthService);
    }

    @Test
    void login_setsStateCookieAndRedirectsToKeycloak() throws Exception {
        when(keycloakClient.getAuthorizationUrl(anyString()))
                .thenReturn("http://keycloak/auth?state=test");

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.login(response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://keycloak/auth?state=test");

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).contains("oauth_state=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Max-Age=300");
    }

    @Test
    void callback_invalidState_returns400() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("oauth_state", "correct-state"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.callback("auth-code", "wrong-state", request, response, null);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void callback_validState_setsCookiesAndRedirects() throws Exception {
        TokenResponse tokens = new TokenResponse();
        tokens.setAccessToken("access-token-value");
        tokens.setRefreshToken("refresh-token-value");
        tokens.setIdToken("id-token-value");
        tokens.setExpiresIn(300);
        tokens.setRefreshExpiresIn(1800);

        when(keycloakClient.handleCallback("auth-code")).thenReturn(tokens);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("oauth_state", "valid-state"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        controller.callback("auth-code", "valid-state", request, response, null);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/dashboard");
        assertThat(session.getAttribute("id_token")).isEqualTo("id-token-value");

        String allCookies = String.join("; ", response.getHeaders("Set-Cookie"));
        assertThat(allCookies).contains("access_token=access-token-value");
        assertThat(allCookies).contains("refresh_token=refresh-token-value");
    }

    @Test
    void callback_clearsStateCookie() throws Exception {
        TokenResponse tokens = new TokenResponse();
        tokens.setAccessToken("at");
        tokens.setRefreshToken("rt");
        tokens.setExpiresIn(300);
        tokens.setRefreshExpiresIn(1800);
        when(keycloakClient.handleCallback(anyString())).thenReturn(tokens);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("oauth_state", "s"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.callback("code", "s", request, response, null);

        String allCookies = String.join("; ", response.getHeaders("Set-Cookie"));
        assertThat(allCookies).contains("oauth_state=;");
        assertThat(allCookies).contains("Max-Age=0");
    }

    @Test
    void logout_clearsCookiesAndRedirectsToKeycloak() throws Exception {
        when(keycloakClient.getLogoutUrl(anyString()))
                .thenReturn("http://keycloak/logout?id_token_hint=my-id");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(KeycloakProperties.REFRESH_TOKEN_COOKIE, "rt-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("id_token", "my-id");
        request.setSession(session);

        controller.logout(request, session, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://keycloak/logout?id_token_hint=my-id");
        verify(keycloakClient).revokeToken("rt-value");

        String allCookies = String.join("; ", response.getHeaders("Set-Cookie"));
        assertThat(allCookies).contains("access_token=;");
        assertThat(allCookies).contains("refresh_token=;");
    }

    @Test
    void logout_continuesEvenWhenRevokeTokenFails() throws Exception {
        doThrow(new KeycloakClient.KeycloakAuthException("fail", new RuntimeException()))
                .when(keycloakClient).revokeToken(anyString());
        when(keycloakClient.getLogoutUrl(any())).thenReturn("http://keycloak/logout");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(KeycloakProperties.REFRESH_TOKEN_COOKIE, "rt-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        controller.logout(request, session, response);

        assertThat(response.getStatus()).isEqualTo(302);
    }

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.refresh(request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(keycloakClient);
    }

    @Test
    void refresh_withCookie_returnsNoContentAndUpdatesCookies() throws Exception {
        TokenResponse tokens = new TokenResponse();
        tokens.setAccessToken("new-access-token");
        tokens.setRefreshToken("new-refresh-token");
        tokens.setExpiresIn(300);
        tokens.setRefreshExpiresIn(1800);
        when(keycloakClient.refreshToken("old-rt")).thenReturn(tokens);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(KeycloakProperties.REFRESH_TOKEN_COOKIE, "old-rt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        var result = controller.refresh(request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        String allCookies = String.join("; ", response.getHeaders("Set-Cookie"));
        assertThat(allCookies).contains("access_token=new-access-token");
        assertThat(allCookies).contains("refresh_token=new-refresh-token");
    }
}
