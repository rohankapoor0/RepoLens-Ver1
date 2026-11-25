package com.repolens.web.validation;

import com.repolens.web.exception.InvalidUsernameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubUsernameValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"octocat", "torvalds", "rohankapoor0", "john-doe", "a", "123", "a-b-c"})
    @DisplayName("Valid standard usernames pass validation and normalization")
    void shouldAcceptValidStandardUsernames(String username) {
        GithubUsernameValidator.validate(username);
        assertThat(GithubUsernameValidator.normalize(username)).isEqualTo(username);
    }

    @ParameterizedTest
    @ValueSource(strings = {"@octocat", "@torvalds", "@rohankapoor0", " @john-doe ", "@a", "@123"})
    @DisplayName("Valid @username inputs are accepted and normalized by stripping '@'")
    void shouldAcceptAndNormalizeAtUsernames(String username) {
        GithubUsernameValidator.validate(username);
        String normalized = GithubUsernameValidator.normalize(username);
        assertThat(normalized).doesNotStartWith("@");
        assertThat(normalized).isEqualTo(username.trim().substring(1).trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-invalid", "invalid-", "in--valid", "@-invalid", "@invalid-", "has spaces", "user@name", "user#name", "user$name"})
    @DisplayName("Invalid usernames violate GitHub constraints and throw InvalidUsernameException")
    void shouldRejectInvalidUsernames(String username) {
        assertThatThrownBy(() -> GithubUsernameValidator.validate(username))
                .isInstanceOf(InvalidUsernameException.class);
    }

    @Test
    @DisplayName("Null or blank usernames throw InvalidUsernameException")
    void shouldRejectNullOrBlank() {
        assertThatThrownBy(() -> GithubUsernameValidator.validate(null))
                .isInstanceOf(InvalidUsernameException.class);

        assertThatThrownBy(() -> GithubUsernameValidator.validate(""))
                .isInstanceOf(InvalidUsernameException.class);

        assertThatThrownBy(() -> GithubUsernameValidator.validate("   "))
                .isInstanceOf(InvalidUsernameException.class);

        assertThatThrownBy(() -> GithubUsernameValidator.validate("@"))
                .isInstanceOf(InvalidUsernameException.class);
    }
}
