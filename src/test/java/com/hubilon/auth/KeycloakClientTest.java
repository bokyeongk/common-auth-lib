package com.hubilon.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakClientTest {

    private KeycloakProperties properties;

    @BeforeEach
    void setUp() {
        properties = new KeycloakProperties();
        properties.setServerUrl("http://keycloak:8080");
        properties.setRealm("my-realm");
        properties.setClientId("my-service");
        properties.setClientSecret("secret");
        properties.setRedirectUri("http://my-service/auth/callback");
        properties.setPostLogoutRedirectUri("http://my-service/auth/login");
    }

    @Test
    void getAuthorizationUrl_containsRequiredParams() {
        KeycloakClient client = new KeycloakClient(properties, null);

        String url = client.getAuthorizationUrl("test-state-123");

        assertThat(url).startsWith("http://keycloak:8080/realms/my-realm/protocol/openid-connect/auth");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=my-service");
        assertThat(url).contains("redirect_uri=http%3A%2F%2Fmy-service%2Fauth%2Fcallback");
        assertThat(url).contains("scope=openid+profile+email");
        assertThat(url).contains("state=test-state-123");
    }

    @Test
    void getLogoutUrl_containsIdTokenHintAndRedirectUri() {
        KeycloakClient client = new KeycloakClient(properties, null);

        String url = client.getLogoutUrl("my-id-token");

        assertThat(url).startsWith("http://keycloak:8080/realms/my-realm/protocol/openid-connect/logout");
        assertThat(url).contains("id_token_hint=my-id-token");
        assertThat(url).contains("client_id=my-service");
        assertThat(url).contains("post_logout_redirect_uri=http%3A%2F%2Fmy-service%2Fauth%2Flogin");
    }

    @Test
    void postLogoutRedirectUri_defaultsToRedirectUri_whenNotSet() {
        properties.setPostLogoutRedirectUri(null);

        assertThat(properties.getPostLogoutRedirectUri())
                .isEqualTo("http://my-service/auth/callback");
    }
}
