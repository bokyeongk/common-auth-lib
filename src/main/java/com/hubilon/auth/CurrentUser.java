package com.hubilon.auth;

import java.lang.annotation.*;

/**
 * Injects the authenticated {@link UserInfo} into a controller method parameter.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public ResponseEntity<UserInfo> me(@CurrentUser UserInfo user) {
 *     return ResponseEntity.ok(user);
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
