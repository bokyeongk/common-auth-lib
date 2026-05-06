package com.hubilon.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("refresh_expires_in")
    private long refreshExpiresIn;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("session_state")
    private String sessionState;

    @JsonProperty("scope")
    private String scope;

    public static LoginResponse from(TokenResponse t) {
        LoginResponse r = new LoginResponse();
        r.accessToken = t.getAccessToken();
        r.expiresIn = t.getExpiresIn();
        r.refreshExpiresIn = t.getRefreshExpiresIn();
        r.tokenType = t.getTokenType();
        r.sessionState = t.getSessionState();
        r.scope = t.getScope();
        return r;
    }

    public String getAccessToken() { return accessToken; }
    public long getExpiresIn() { return expiresIn; }
    public long getRefreshExpiresIn() { return refreshExpiresIn; }
    public String getTokenType() { return tokenType; }
    public String getSessionState() { return sessionState; }
    public String getScope() { return scope; }
}
