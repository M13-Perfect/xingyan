package org.example.xyyx.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

import java.sql.SQLException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhoneRevealSessionServiceTests {

    @Test
    void missingSessionTableMeansNotActivated() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Object users = currentUserService(jdbc);
        Object service = sessionService(jdbc, users);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("jti", "login-1").build();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("default"), eq(7L), anyString())).thenThrow(
                new BadSqlGrammarException("select", "SELECT COUNT(*) FROM phone_reveal_login_session", new SQLException("missing"))
        );

        Object active = service.getClass().getMethod("isActivated", String.class, Long.class, Jwt.class)
                .invoke(service, "default", 7L, jwt);

        assertFalse((Boolean) active);
    }

    @Test
    void tokenFingerprintFallbackProvidesServerSideSessionKey() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Object users = currentUserService(jdbc);
        Jwt jwt = Jwt.withTokenValue("opaque-token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-30T00:00:00Z"))
                .expiresAt(Instant.parse("2026-06-30T01:00:00Z"))
                .build();

        @SuppressWarnings("unchecked")
        java.util.Optional<String> key = (java.util.Optional<String>) users.getClass()
                .getMethod("loginSessionId", Jwt.class)
                .invoke(users, jwt);

        assertTrue(key.isPresent());
        assertFalse(key.get().contains("opaque-token"));
    }

    private static Object currentUserService(JdbcTemplate jdbc) throws Exception {
        return Class.forName("org.example.xyyx.service.CurrentUserService")
                .getConstructor(JdbcTemplate.class)
                .newInstance(jdbc);
    }

    private static Object sessionService(JdbcTemplate jdbc, Object users) throws Exception {
        Class<?> currentUserService = Class.forName("org.example.xyyx.service.CurrentUserService");
        return Class.forName("org.example.xyyx.service.PhoneRevealSessionService")
                .getConstructor(JdbcTemplate.class, currentUserService)
                .newInstance(jdbc, users);
    }
}
