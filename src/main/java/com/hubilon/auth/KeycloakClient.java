package com.hubilon.auth;

import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Keycloak Authorization Code Flow 클라이언트.
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link #getAuthorizationUrl(String)} → Keycloak 로그인 페이지 URL 반환</li>
 *   <li>사용자가 Keycloak에서 로그인 → 서비스 콜백 URL로 code 전달</li>
 *   <li>{@link #handleCallback(String)} → code를 토큰으로 교환</li>
 *   <li>{@link #getLogoutUrl(String)} → Keycloak SSO 세션까지 종료하는 로그아웃 URL 반환</li>
 * </ol>
 */
public class KeycloakClient {

    private final KeycloakProperties properties;
    private final RestTemplate restTemplate;

    public KeycloakClient(KeycloakProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * Keycloak 로그인 페이지로 리다이렉트할 URL을 반환합니다.
     *
     * <p>state 파라미터는 CSRF 방지용 랜덤 값으로, 컨트롤러에서 세션에 저장한 뒤
     * 콜백에서 검증해야 합니다.
     *
     * @param state CSRF 방지용 랜덤 문자열 (UUID 권장)
     * @return Keycloak authorization URL
     */
    public String getAuthorizationUrl(String state) {
        return UriComponentsBuilder
                .fromHttpUrl(properties.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", properties.getScope())
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /**
     * Keycloak이 콜백으로 전달한 authorization code를 토큰으로 교환합니다.
     *
     * @param code Keycloak이 발급한 authorization code
     * @return access_token, refresh_token, id_token을 포함한 응답
     * @throws KeycloakAuthException 코드가 유효하지 않거나 만료된 경우
     */
    public TokenResponse handleCallback(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("code", code);
        body.add("redirect_uri", properties.getRedirectUri());

        return postToTokenEndpoint(body);
    }

    /**
     * Refresh Token으로 새 Access Token을 발급합니다.
     *
     * @throws KeycloakAuthException refresh token이 만료된 경우
     */
    public TokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("refresh_token", refreshToken);

        return postToTokenEndpoint(body);
    }

    /**
     * Keycloak SSO 세션을 포함한 로그아웃 URL을 반환합니다 (브라우저 리다이렉트용).
     *
     * <p>이 URL로 브라우저를 리다이렉트하면 Keycloak이 SSO 세션을 만료시키고
     * {@code keycloak.post-logout-redirect-uri}로 다시 리다이렉트합니다.
     *
     * @param idToken 로그인 시 발급된 id_token (id_token_hint 파라미터)
     * @return Keycloak logout URL
     */
    public String getLogoutUrl(String idToken) {
        return UriComponentsBuilder
                .fromHttpUrl(properties.getLogoutUri())
                .queryParam("id_token_hint", idToken)
                .queryParam("post_logout_redirect_uri", properties.getPostLogoutRedirectUri())
                .queryParam("client_id", properties.getClientId())
                .build()
                .toUriString();
    }

    /**
     * 백채널 로그아웃 - refresh token을 즉시 무효화합니다.
     *
     * <p>브라우저 없이 서버에서 직접 토큰을 폐기할 때 사용합니다.
     * SSO 세션 전체를 종료하려면 {@link #getLogoutUrl(String)}을 사용하세요.
     *
     * @throws KeycloakAuthException 서버 오류 발생 시
     */
    public void revokeToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("refresh_token", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            restTemplate.postForEntity(
                    properties.getLogoutUri(),
                    new HttpEntity<>(body, headers),
                    Void.class
            );
        } catch (HttpClientErrorException e) {
            throw new KeycloakAuthException("Token revocation failed: " + e.getMessage(), e);
        }
    }

    private TokenResponse postToTokenEndpoint(MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    properties.getTokenUri(),
                    new HttpEntity<>(body, headers),
                    TokenResponse.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw new KeycloakAuthException("Keycloak token request failed: " + e.getResponseBodyAsString(), e);
        }
    }

    public static class KeycloakAuthException extends RuntimeException {
        public KeycloakAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
