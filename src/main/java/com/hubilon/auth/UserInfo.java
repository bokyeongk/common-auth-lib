package com.hubilon.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UserInfo {

    private final String userId;
    private final String username;
    private final String email;
    private final List<String> roles;
    private final Map<String, Object> attributes;

    public UserInfo(String userId, String username, String email, List<String> roles) {
        this(userId, username, email, roles, Collections.emptyMap());
    }

    public UserInfo(String userId, String username, String email, List<String> roles,
                    Map<String, Object> attributes) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.roles = Collections.unmodifiableList(roles);
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public List<String> getRoles() { return roles; }
    public Map<String, Object> getAttributes() { return attributes; }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public <T> Optional<T> getAttributeAs(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(type.cast(value));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        return "UserInfo{userId='" + userId + "', username='" + username + "', roles=" + roles + "}";
    }
}
