package com.hubilon.auth;

import java.util.List;

/**
 * Thread-local store for the authenticated user of the current request.
 * Set by KeycloakTokenFilter; cleared after each request completes.
 */
public final class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private UserContext() {}

    public static void set(UserInfo userInfo) {
        HOLDER.set(userInfo);
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static String getUserId() {
        UserInfo info = get();
        return info != null ? info.getUserId() : null;
    }

    public static String getUsername() {
        UserInfo info = get();
        return info != null ? info.getUsername() : null;
    }

    public static String getEmail() {
        UserInfo info = get();
        return info != null ? info.getEmail() : null;
    }

    public static List<String> getRoles() {
        UserInfo info = get();
        return info != null ? info.getRoles() : List.of();
    }

    public static boolean hasRole(String role) {
        UserInfo info = get();
        return info != null && info.hasRole(role);
    }
}
