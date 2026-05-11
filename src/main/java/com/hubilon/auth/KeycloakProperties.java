package com.hubilon.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    /** OIDC scope. 기본값: openid profile email */
    private String scope = "openid profile email";

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
        private String register = "/auth/register";

        public String getLogin()    { return login; }
        public void setLogin(String login)       { this.login = login; }
        public String getLogout()   { return logout; }
        public void setLogout(String logout)     { this.logout = logout; }
        public String getCallback() { return callback; }
        public void setCallback(String callback) { this.callback = callback; }
        public String getRefresh()  { return refresh; }
        public void setRefresh(String refresh)   { this.refresh = refresh; }
        public String getRegister() { return register; }
        public void setRegister(String register) { this.register = register; }
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

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

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

    public String getIssuerUri() {
        return serverUrl + "/realms/" + realm;
    }

    public String getAdminUsersUri() {
        return serverUrl + "/admin/realms/" + realm + "/users";
    }
}
