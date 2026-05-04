package com.hubilon.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KeycloakTokenFilterTest {

    private JwtDecoder jwtDecoder;
    private KeycloakProperties properties;
    private KeycloakTokenFilter filter;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        properties = new KeycloakProperties();
        properties.setClientId("my-service");
        properties.setPermitAllPaths(List.of("/public/**", "/health"));
        filter = new KeycloakTokenFilter(jwtDecoder, properties);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void returns401_whenAuthorizationHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void returns401_whenTokenIsInvalid() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtException("bad token"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("Authorization", "Bearer bad.token.here");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void populatesUserContext_whenTokenIsValid() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("preferred_username", "john")
                .claim("email", "john@example.com")
                .claim("realm_access", Map.of("roles", List.of("user", "admin")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(jwtDecoder.decode("valid.token")).thenReturn(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("Authorization", "Bearer valid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserInfo[] captured = new UserInfo[1];
        filter.doFilterInternal(request, response, (req, res) -> captured[0] = UserContext.get());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].getUserId()).isEqualTo("user-123");
        assertThat(captured[0].getUsername()).isEqualTo("john");
        assertThat(captured[0].getRoles()).containsExactlyInAnyOrder("user", "admin");
    }

    @Test
    void skipsFilter_forPermitAllPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/info");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void collectsCustomClaims_intoAttributes() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("preferred_username", "john")
                .claim("email", "john@example.com")
                .claim("department", "engineering")
                .claim("employee_id", 42)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(jwtDecoder.decode("valid.token")).thenReturn(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("Authorization", "Bearer valid.token");

        UserInfo[] captured = new UserInfo[1];
        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> captured[0] = UserContext.get());

        assertThat(captured[0].getAttribute("department")).isEqualTo("engineering");
        assertThat(captured[0].getAttribute("employee_id")).isEqualTo(42);
    }

    @Test
    void excludesKnownClaims_fromAttributes() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("preferred_username", "john")
                .claim("email", "john@example.com")
                .claim("acr", "1")
                .claim("session_state", "abc-session")
                .claim("department", "engineering")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(jwtDecoder.decode("valid.token")).thenReturn(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("Authorization", "Bearer valid.token");

        UserInfo[] captured = new UserInfo[1];
        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> captured[0] = UserContext.get());

        assertThat(captured[0].getAttributes()).doesNotContainKey("acr");
        assertThat(captured[0].getAttributes()).doesNotContainKey("session_state");
        assertThat(captured[0].getAttributes()).doesNotContainKey("sub");
        assertThat(captured[0].getAttributes()).doesNotContainKey("email");
        assertThat(captured[0].getAttributes()).containsKey("department");
    }

    @Test
    void returnsEmptyAttributes_whenNoCustomClaims() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .claim("preferred_username", "john")
                .claim("email", "john@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(jwtDecoder.decode("valid.token")).thenReturn(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("Authorization", "Bearer valid.token");

        UserInfo[] captured = new UserInfo[1];
        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (req, res) -> captured[0] = UserContext.get());

        assertThat(captured[0].getAttributes()).isEmpty();
    }

    @Test
    void clearsUserContext_afterRequest() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-456")
                .claim("preferred_username", "jane")
                .claim("email", "jane@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(jwtDecoder.decode("valid.token")).thenReturn(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        request.addHeader("Authorization", "Bearer valid.token");

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(UserContext.get()).isNull();
    }
}
