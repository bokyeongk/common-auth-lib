package com.hubilon.auth;

/**
 * Keycloak 필수 설정 누락 시 발생하는 예외.
 *
 * <p>소비 서비스에서 이 예외를 명시적으로 catch하여 설정 오류를 확인할 수 있다:
 * <pre>
 * try {
 *     keycloakClient.loginWithPassword(username, password);
 * } catch (KeycloakConfigurationException e) {
 *     // keycloak.* 설정이 누락된 경우
 *     log.error("Keycloak 설정 오류: {}", e.getMessage());
 * }
 * </pre>
 */
public class KeycloakConfigurationException extends RuntimeException {

    public KeycloakConfigurationException(String message) {
        super(message);
    }
}
