package org.example.xyyx.task;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import org.example.xyyx.service.CasdoorAdminService;

class StaffCasdoorBackfillTaskTests {

    private static Map<String, Object> row(String password, String role) {
        return Map.of("password", password, "role", role);
    }

    private static void run(StaffCasdoorBackfillTask task, boolean dryRun) {
        task.run(new DefaultApplicationArguments(
                "staff-casdoor-backfill",
                "--usernames=alice",
                "--dryRun=" + dryRun
        ));
    }

    @Test
    void dryRunNeverCallsCasdoorOrWritesLocalPassword() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CasdoorAdminService casdoorAdminService = mock(CasdoorAdminService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.queryForList(anyString(), eq("alice")))
                .thenReturn(List.of(row("plaintext123", "staff")));

        run(new StaffCasdoorBackfillTask(jdbcTemplate, casdoorAdminService, passwordEncoder, mock(ConfigurableApplicationContext.class)), true);

        verify(casdoorAdminService, never()).createUser(anyString(), anyString(), anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void realRunCreatesCasdoorUserThenHashesLocalPassword() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CasdoorAdminService casdoorAdminService = mock(CasdoorAdminService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.queryForList(anyString(), eq("alice")))
                .thenReturn(List.of(row("plaintext123", "staff")));
        when(passwordEncoder.encode("plaintext123")).thenReturn("$2a$10$hashedvalue");

        run(new StaffCasdoorBackfillTask(jdbcTemplate, casdoorAdminService, passwordEncoder, mock(ConfigurableApplicationContext.class)), false);

        verify(casdoorAdminService, times(1)).createUser("alice", "plaintext123", "alice");
        verify(jdbcTemplate, times(1)).update(anyString(), eq("$2a$10$hashedvalue"), eq("alice"));
    }

    @Test
    void alreadyHashedPasswordIsSkipped() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CasdoorAdminService casdoorAdminService = mock(CasdoorAdminService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.queryForList(anyString(), eq("alice")))
                .thenReturn(List.of(row("$2a$10$" + "a".repeat(53), "staff")));

        run(new StaffCasdoorBackfillTask(jdbcTemplate, casdoorAdminService, passwordEncoder, mock(ConfigurableApplicationContext.class)), false);

        verify(casdoorAdminService, never()).createUser(anyString(), anyString(), anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void adminRoleIsNeverTouched() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CasdoorAdminService casdoorAdminService = mock(CasdoorAdminService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.queryForList(anyString(), eq("alice")))
                .thenReturn(List.of(row("plaintext123", "admin")));

        run(new StaffCasdoorBackfillTask(jdbcTemplate, casdoorAdminService, passwordEncoder, mock(ConfigurableApplicationContext.class)), false);

        verify(casdoorAdminService, never()).createUser(anyString(), anyString(), anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void casdoorFailureLeavesLocalPasswordUntouchedSoRetryIsSafe() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CasdoorAdminService casdoorAdminService = mock(CasdoorAdminService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(jdbcTemplate.queryForList(anyString(), eq("alice")))
                .thenReturn(List.of(row("plaintext123", "staff")));
        doThrow(new IllegalStateException("Casdoor 创建用户失败: boom"))
                .when(casdoorAdminService).createUser("alice", "plaintext123", "alice");

        run(new StaffCasdoorBackfillTask(jdbcTemplate, casdoorAdminService, passwordEncoder, mock(ConfigurableApplicationContext.class)), false);

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
