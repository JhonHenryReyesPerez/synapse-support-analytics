package com.roles.usermanagement;

import com.roles.usermanagement.domain.service.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTests {

    @ParameterizedTest
    @ValueSource(strings = {"Password123!", "Ab#def", "Clave_Segura"})
    void acceptsPasswordsMeetingPolicy(String password) {
        assertThat(PasswordPolicy.isValid(password)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Ab#de", "abcdef", "password1!", "PASSWORD1", "Password 1"})
    void rejectsPasswordsBreakingPolicy(String password) {
        assertThat(PasswordPolicy.violation(password)).isPresent();
    }

    @Test
    void rejectsPasswordsLongerThanBcryptLimit() {
        assertThat(PasswordPolicy.isValid("A!" + "a".repeat(70))).isTrue();   // 72 bytes
        assertThat(PasswordPolicy.isValid("A!" + "a".repeat(71))).isFalse();  // 73 bytes
        assertThat(PasswordPolicy.isValid("A!" + "ñ".repeat(36))).isFalse();  // 38 caracteres, 74 bytes
    }
}
