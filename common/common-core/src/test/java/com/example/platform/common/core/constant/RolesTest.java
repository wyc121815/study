package com.example.platform.common.core.constant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RolesTest {

    @Test
    void adminMatchingIgnoresCaseAndSurroundingWhitespace() {
        assertThat(Roles.isAdmin("ADMIN")).isTrue();
        assertThat(Roles.isAdmin("admin")).isTrue();
        assertThat(Roles.isAdmin("Admin")).isTrue();
        assertThat(Roles.isAdmin("  admin  ")).isTrue();
    }

    @Test
    void nonAdminsAreNotTreatedAsAdmin() {
        assertThat(Roles.isAdmin("USER")).isFalse();
        assertThat(Roles.isAdmin("user")).isFalse();
        assertThat(Roles.isAdmin("boss")).isFalse();
        assertThat(Roles.isAdmin("")).isFalse();
        assertThat(Roles.isAdmin(null)).isFalse();
    }

    @Test
    void normalizeReturnsCanonicalUppercaseOrNull() {
        assertThat(Roles.normalize(" admin ")).isEqualTo("ADMIN");
        assertThat(Roles.normalize("user")).isEqualTo("USER");
        assertThat(Roles.normalize("boss")).isNull();
        assertThat(Roles.normalize(null)).isNull();
    }

    @Test
    void isValidRejectsUnknownAndBlankRoles() {
        assertThat(Roles.isValid("ADMIN")).isTrue();
        assertThat(Roles.isValid(" user ")).isTrue();
        assertThat(Roles.isValid("boss")).isFalse();
        assertThat(Roles.isValid("  ")).isFalse();
        assertThat(Roles.isValid(null)).isFalse();
    }

    @Test
    void canonicalKeepsUnknownRoleForTroubleshooting() {
        assertThat(Roles.canonical("admin")).isEqualTo("ADMIN");
        assertThat(Roles.canonical("boss")).isEqualTo("boss");
    }
}
