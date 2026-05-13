package com.hubilon.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    private String serverUrl;
    private String realm;
    private String clientId;
    private String clientSecret;

    /** Authorization Code Flow 콜백 URI (각 서비스마다 다르게 설정) */
    private String redirectUri;

    /** 로그아웃 후 리다이렉트할 URI. 미설정 시 redirectUri 기준 경로를 사용 */
    private String postLogoutRedirectUri;

    /** 고정 scope. openid, profile은 항상 포함된다. */
    private static final List<String> FIXED_SCOPES = List.of("openid", "profile");

    /** 추가 scope 목록. 기본값: [email] */
    private List<String> additionalScopes = new ArrayList<>(List.of("email"));

    /** 로그인 완료(callback) 후 리다이렉트할 URI. 기본값: / */
    private String postLoginRedirectUri = "/";

    /**
     * 쿠키에 Secure 플래그 적용 여부.
     * 운영(HTTPS) 환경: true / 로컬 개발(HTTP) 환경: false
     */
    private boolean secureCookie = true;

    /** access_token 쿠키 이름 */
    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    /** refresh_token 쿠키 이름 */
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    /** OAuth state 쿠키 이름. 기본값: oauth_state */
    private String oauthStateCookie = "oauth_state";

    /** 세션에 id_token 저장 시 사용할 키. 기본값: id_token */
    private String sessionIdTokenKey = "id_token";

    /** Ant-pattern paths that bypass token validation (e.g. /public/**, /health) */
    private List<String> permitAllPaths = new ArrayList<>();

    private Uri uri = new Uri();
    private AuthController authController = new AuthController();

    public static class AuthController {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public AuthController getAuthController() { return authController; }
    public void setAuthController(AuthController authController) { this.authController = authController; }

    public static class Uri {
        private String login    = "/auth/login";
        private String logout   = "/auth/logout";
        private String callback = "/auth/callback";
        private String refresh  = "/auth/refresh";
        private String checkUsername  = "/auth/check-username";
        private String checkEmail     = "/auth/check-email";

        public String getLogin()    { return login; }
        public void setLogin(String login)       { this.login = login; }
        public String getLogout()   { return logout; }
        public void setLogout(String logout)     { this.logout = logout; }
        public String getCallback() { return callback; }
        public void setCallback(String callback) { this.callback = callback; }
        public String getRefresh()  { return refresh; }
        public void setRefresh(String refresh)   { this.refresh = refresh; }
        public String getCheckUsername() { return checkUsername; }
        public void setCheckUsername(String checkUsername) { this.checkUsername = checkUsername; }
        public String getCheckEmail() { return checkEmail; }
        public void setCheckEmail(String checkEmail) { this.checkEmail = checkEmail; }

        public String[] getPermitAllPaths() {
            return new String[]{ login, logout, callback, refresh, checkUsername, checkEmail };
        }
    }

    public Uri getUri() { return uri; }
    public void setUri(Uri uri) { this.uri = uri; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getRealm() { return realm; }
    public void setRealm(String realm) { this.realm = realm; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getPostLogoutRedirectUri() {
        return postLogoutRedirectUri != null ? postLogoutRedirectUri : redirectUri;
    }
    public void setPostLogoutRedirectUri(String postLogoutRedirectUri) {
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    /** openid, profile은 고정. additionalScopes가 뒤에 붙어 최종 scope 문자열을 구성한다. */
    public String getScope() {
        List<String> all = new ArrayList<>(FIXED_SCOPES);
        all.addAll(additionalScopes);
        return String.join(" ", all);
    }

    public List<String> getAdditionalScopes() { return additionalScopes; }
    public void setAdditionalScopes(List<String> additionalScopes) { this.additionalScopes = additionalScopes; }

    public String getPostLoginRedirectUri() { return postLoginRedirectUri; }
    public void setPostLoginRedirectUri(String postLoginRedirectUri) { this.postLoginRedirectUri = postLoginRedirectUri; }

    public boolean isSecureCookie() { return secureCookie; }
    public void setSecureCookie(boolean secureCookie) { this.secureCookie = secureCookie; }

    public String getOauthStateCookie() { return oauthStateCookie; }
    public void setOauthStateCookie(String oauthStateCookie) { this.oauthStateCookie = oauthStateCookie; }

    public String getSessionIdTokenKey() { return sessionIdTokenKey; }
    public void setSessionIdTokenKey(String sessionIdTokenKey) { this.sessionIdTokenKey = sessionIdTokenKey; }

    public List<String> getPermitAllPaths() { return permitAllPaths; }
    public void setPermitAllPaths(List<String> permitAllPaths) { this.permitAllPaths = permitAllPaths; }

    public String getAuthorizationUri() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/auth";
    }

    public String getTokenUri() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String getLogoutUri() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
    }

    public String getJwksUri() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
    }

    public String getUserInfoUri() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";
    }

    public String getIssuerUri() {
        return serverUrl + "/realms/" + realm;
    }

    public String getAdminUsersUri() {
        return serverUrl + "/admin/realms/" + realm + "/users";
    }

    /**
     * 누락된 필수 설정 키 목록을 반환한다.
     * 비어 있으면 설정이 완전한 것이다.
     */
    public List<String> getMissingRequiredFields() {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(serverUrl))  missing.add("keycloak.server-url");
        if (!StringUtils.hasText(realm))      missing.add("keycloak.realm");
        if (!StringUtils.hasText(clientId))   missing.add("keycloak.client-id");
        if (!StringUtils.hasText(clientSecret)) missing.add("keycloak.client-secret");
        if (!StringUtils.hasText(redirectUri))  missing.add("keycloak.redirect-uri");
        return missing;
    }

    /**
     * 필수 설정이 모두 존재하는지 검증한다.
     *
     * @throws KeycloakConfigurationException 필수 설정이 누락된 경우
     */
    public void validate() {
        List<String> missing = getMissingRequiredFields();
        if (!missing.isEmpty()) {
            throw new KeycloakConfigurationException(
                    "Required Keycloak properties are missing: " + String.join(", ", missing));
        }
    }
}
