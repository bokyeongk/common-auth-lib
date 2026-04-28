package com.hubilon.auth;

import java.util.Collections;
import java.util.List;

public class UserInfo {

    private final String userId;
    private final String username;
    private final String email;
    private final List<String> roles;

    public UserInfo(String userId, String username, String email, List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.roles = Collections.unmodifiableList(roles);
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public List<String> getRoles() { return roles; }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        return "UserInfo{userId='" + userId + "', username='" + username + "', roles=" + roles + "}";
    }
}
