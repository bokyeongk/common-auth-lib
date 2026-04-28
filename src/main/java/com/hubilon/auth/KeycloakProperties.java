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

    /** Ant-pattern paths that bypass token validation (e.g. /public/**, /health) */
    private List<String> permitAllPaths = new ArrayList<>();

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
}
