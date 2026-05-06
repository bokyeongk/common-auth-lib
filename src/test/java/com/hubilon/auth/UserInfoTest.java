package com.hubilon.auth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserInfoTest {

    @Test
    void getAttribute_returnsValue_whenKeyExists() {
        UserInfo user = new UserInfo("u1", "john", "john@example.com", List.of(),
                Map.of("department", "engineering"));

        assertThat(user.getAttribute("department")).isEqualTo("engineering");
    }

    @Test
    void getAttribute_returnsNull_whenKeyMissing() {
        UserInfo user = new UserInfo("u1", "john", "john@example.com", List.of(), Map.of());

        assertThat(user.getAttribute("nonexistent")).isNull();
    }

    @Test
    void getAttributeAs_returnsTypedValue_onSuccess() {
        UserInfo user = new UserInfo("u1", "john", "john@example.com", List.of(),
                Map.of("employee_id", 42));

        Optional<Integer> result = user.getAttributeAs("employee_id", Integer.class);

        assertThat(result).contains(42);
    }

    @Test
    void getAttributeAs_returnsEmpty_onTypeMismatch() {
        UserInfo user = new UserInfo("u1", "john", "john@example.com", List.of(),
                Map.of("employee_id", 42));

        Optional<String> result = user.getAttributeAs("employee_id", String.class);

        assertThat(result).isEmpty();
    }

    @Test
    void getAttributeAs_returnsEmpty_whenKeyMissing() {
        UserInfo user = new UserInfo("u1", "john", "john@example.com", List.of(), Map.of());

        Optional<String> result = user.getAttributeAs("nonexistent", String.class);

        assertThat(result).isEmpty();
    }

    @Test
    void attributes_areImmutable() {
        UserInfo user = new UserInfo("u1", "john", "john@example.com", List.of(),
                Map.of("department", "engineering"));

        assertThatThrownBy(() -> user.getAttributes().put("new_key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fourArgConstructor_producesEmptyAttributes() {
        UserInfo user = new UserInfo("u1", "john", "john@example.com", List.of("admin"));

        assertThat(user.getAttributes()).isEmpty();
    }

    @Test
    void toString_doesNotExposeAttributes() {
        UserInfo user = new UserInfo("u1", "john", "john@example.com", List.of(),
                Map.of("secret_claim", "sensitive-value"));

        assertThat(user.toString()).doesNotContain("secret_claim");
        assertThat(user.toString()).doesNotContain("sensitive-value");
    }
}
