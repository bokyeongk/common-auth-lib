package com.hubilon.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Admin Token 캐싱: 만료 30초 전에 재발급하여 불필요한 네트워크 호출 방지
    private volatile String cachedAdminToken;
    private volatile long adminTokenExpiresAt;

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
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(properties.getLogoutUri())
                .queryParam("post_logout_redirect_uri", properties.getPostLogoutRedirectUri())
                .queryParam("client_id", properties.getClientId());

        if (org.springframework.util.StringUtils.hasText(idToken)) {
            builder.queryParam("id_token_hint", idToken);
        }

        return builder.build().toUriString();
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
        } catch (RestClientException e) {
            throw new KeycloakAuthException("Token revocation failed: " + e.getMessage(), e);
        }
    }

    /**
     * ROPC(Resource Owner Password Credentials) Flow로 토큰을 발급한다.
     *
     * <p><b>전제조건:</b> Keycloak Admin에서 해당 Client의 {@code Direct Access Grants Enabled} 옵션이
     * 활성화되어 있어야 한다. OAuth 2.1에서 이 Flow는 공식 제거되었으므로 사용에 주의한다.
     *
     * @param username Keycloak username 또는 email
     * @param password 사용자 비밀번호
     * @throws KeycloakAuthException 인증 실패 또는 Keycloak 서버 오류 시
     */
    public TokenResponse loginWithPassword(String username, String password) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("username", username);
        body.add("password", password);

        return postToTokenEndpoint(body);
    }

    /**
     * Keycloak Admin API 호출에 사용할 Service Account 토큰을 반환한다.
     * 만료 30초 전까지 캐시된 토큰을 재사용한다.
     */
    private synchronized String obtainAdminToken() {
        if (cachedAdminToken != null && System.currentTimeMillis() < adminTokenExpiresAt - 30_000L) {
            return cachedAdminToken;
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());

        try {
            TokenResponse token = postToTokenEndpoint(body);
            cachedAdminToken = token.getAccessToken();
            adminTokenExpiresAt = System.currentTimeMillis() + token.getExpiresIn() * 1000L;
            return cachedAdminToken;
        } catch (KeycloakAuthException e) {
            throw new KeycloakUnavailableException("Failed to obtain admin token", e);
        }
    }

    /**
     * Keycloak Admin API로 신규 사용자를 등록한다.
     *
     * @throws KeycloakUnavailableException Admin 토큰 발급 실패 시
     * @throws KeycloakConflictException    username 또는 email 중복 시
     * @throws KeycloakAuthException        그 외 Keycloak 오류 시
     */
    public void register(RegisterRequest request) {
        String adminToken = obtainAdminToken();

        Map<String, Object> body = new HashMap<>();
        body.put("username", request.getUsername());
        body.put("email", request.getEmail());
        body.put("enabled", true);
        body.put("emailVerified", false);

        if (org.springframework.util.StringUtils.hasText(request.getFirstName())) {
            body.put("firstName", request.getFirstName());
        }
        if (org.springframework.util.StringUtils.hasText(request.getLastName())) {
            body.put("lastName", request.getLastName());
        }

        body.put("credentials", List.of(Map.of(
                "type", "password",
                "value", request.getPassword(),
                "temporary", false
        )));

        if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
            Map<String, List<String>> attrs = new HashMap<>();
            request.getAttributes().forEach((k, v) ->
                    attrs.put(k, List.of(String.valueOf(v))));
            body.put("attributes", attrs);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        try {
            restTemplate.postForEntity(
                    properties.getAdminUsersUri(),
                    new HttpEntity<>(body, headers),
                    Void.class
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                String errorMessage = parseErrorMessage(e.getResponseBodyAsString());
                throw new KeycloakConflictException(errorMessage);
            }
            throw new KeycloakAuthException("Keycloak register failed: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new KeycloakAuthException("Keycloak register failed: " + e.getMessage(), e);
        }
    }

    /**
     * Admin API로 사용자 목록을 조회해 특정 파라미터 기준으로 존재 여부를 반환한다.
     * exact=true: Keycloak이 완전 일치 검색만 수행하도록 강제 (부분 일치 오탐 방지)
     */
    private boolean userExistsByParam(String paramName, String paramValue) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getAdminUsersUri())
                .queryParam(paramName, paramValue)
                .queryParam("exact", "true")
                .encode()
                .build()
                .toUriString();

        String adminToken = obtainAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            List<?> users = response.getBody();
            return users != null && !users.isEmpty();
        } catch (HttpServerErrorException e) {
            throw new KeycloakUnavailableException("Keycloak server error during user lookup: " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            throw new KeycloakAuthException("Keycloak client error during user lookup: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new KeycloakUnavailableException("Network error during user lookup: " + e.getMessage(), e);
        }
    }

    public boolean existsByUsername(String username) {
        return userExistsByParam("username", username);
    }

    public boolean existsByEmail(String email) {
        return userExistsByParam("email", email);
    }

    private String parseErrorMessage(String responseBody) {
        try {
            Map<String, Object> map = objectMapper.readValue(responseBody,
                    new TypeReference<Map<String, Object>>() {});
            Object msg = map.get("errorMessage");
            return msg != null ? msg.toString() : responseBody;
        } catch (Exception ignored) {
            return responseBody;
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
            TokenResponse tokenResponse = response.getBody();
            if (tokenResponse == null) {
                throw new KeycloakAuthException("Keycloak returned empty token response", null);
            }
            return tokenResponse;
        } catch (RestClientException e) {
            throw new KeycloakAuthException("Keycloak token request failed: " + e.getMessage(), e);
        }
    }

    public static class KeycloakAuthException extends RuntimeException {
        public KeycloakAuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class KeycloakConflictException extends RuntimeException {
        private final String errorMessage;
        public KeycloakConflictException(String errorMessage) {
            super(errorMessage);
            this.errorMessage = errorMessage;
        }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class KeycloakUnavailableException extends RuntimeException {
        public KeycloakUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
