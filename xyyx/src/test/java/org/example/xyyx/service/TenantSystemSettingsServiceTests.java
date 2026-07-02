package org.example.xyyx.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantSystemSettingsServiceTests {

    @Test
    void missingSettingsTableFallsBackToClickToSessionVisibleAndPageSize20() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq("default"))).thenThrow(
                new BadSqlGrammarException("select", "SELECT * FROM tenant_system_settings", new SQLException("missing"))
        );

        Object service = service(jdbc, null, null);
        Object settings = service.getClass().getMethod("getEffectiveSettings", String.class).invoke(service, "default");

        assertEquals("default", invoke(settings, "tenantId"));
        assertEquals("CLICK_TO_SESSION_VISIBLE", invoke(settings, "phoneDisplayPolicy").toString());
        assertEquals(20, invoke(settings, "orderPageSize"));
        assertEquals(3, invoke(settings, "revisitDeadlineDays"));
    }

    @Test
    void updateRejectsInvalidOrderPageSize() throws Exception {
        Class<?> currentUserClass = Class.forName("org.example.xyyx.service.CurrentUserService$CurrentUser");
        Object service = service(mock(JdbcTemplate.class), mock(Class.forName("org.example.xyyx.service.PhoneRevealSessionService")), mock(Class.forName("org.example.xyyx.service.CurrentUserService")));
        Object admin = currentUserClass.getConstructor(String.class, String.class).newInstance("admin", "admin");
        Method update = service.getClass().getMethod(
                "updateSettings", String.class, Map.class, currentUserClass, Long.class, HttpServletRequest.class);

        Exception thrown = assertThrows(Exception.class, () ->
                update.invoke(service, "default", Map.of("phoneDisplayPolicy", "MASKED_ONLY", "orderPageSize", 101), admin, 1L, null));

        Throwable cause = thrown.getCause();
        assertEquals("org.example.xyyx.service.ApiException", cause.getClass().getName());
        assertEquals(HttpStatus.BAD_REQUEST, cause.getClass().getMethod("status").invoke(cause));
    }

    @Test
    void updateRejectsInvalidRevisitDeadlineDays() throws Exception {
        Class<?> currentUserClass = Class.forName("org.example.xyyx.service.CurrentUserService$CurrentUser");
        Object service = service(mock(JdbcTemplate.class), mock(Class.forName("org.example.xyyx.service.PhoneRevealSessionService")), mock(Class.forName("org.example.xyyx.service.CurrentUserService")));
        Object admin = currentUserClass.getConstructor(String.class, String.class).newInstance("admin", "admin");
        Method update = service.getClass().getMethod(
                "updateSettings", String.class, Map.class, currentUserClass, Long.class, HttpServletRequest.class);

        Exception thrown = assertThrows(Exception.class, () ->
                update.invoke(service, "default",
                        Map.of("phoneDisplayPolicy", "MASKED_ONLY", "orderPageSize", 20, "revisitDeadlineDays", 0),
                        admin, 1L, null));

        Throwable cause = thrown.getCause();
        assertEquals("org.example.xyyx.service.ApiException", cause.getClass().getName());
        assertEquals(HttpStatus.BAD_REQUEST, cause.getClass().getMethod("status").invoke(cause));
    }

    @Test
    void updateInvalidatesTenantRevealSessions() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Class<?> sessionClass = Class.forName("org.example.xyyx.service.PhoneRevealSessionService");
        Class<?> currentUserServiceClass = Class.forName("org.example.xyyx.service.CurrentUserService");
        Class<?> currentUserClass = Class.forName("org.example.xyyx.service.CurrentUserService$CurrentUser");
        Object sessions = mock(sessionClass);
        Object users = mock(currentUserServiceClass);
        Object admin = currentUserClass.getConstructor(String.class, String.class).newInstance("admin", "admin");
        when(jdbc.queryForList(anyString(), eq("default")))
                .thenReturn(List.of(Map.of("phone_display_policy", "MASKED_ONLY", "order_page_size", 30, "revisit_deadline_days", 5)))
                .thenReturn(List.of(Map.of("phone_display_policy", "CLICK_TO_SESSION_VISIBLE", "order_page_size", 25, "revisit_deadline_days", 7)));
        Object service = service(jdbc, sessions, users);

        service.getClass()
                .getMethod("updateSettings", String.class, Map.class, currentUserClass, Long.class, HttpServletRequest.class)
                .invoke(service, "default",
                        Map.of("phoneDisplayPolicy", "CLICK_TO_SESSION_VISIBLE", "orderPageSize", 25, "revisitDeadlineDays", 7),
                        admin, 1L, null);

        sessionClass.getMethod("invalidateForTenant", String.class).invoke(verify(sessions), "default");
    }

    private static Object service(JdbcTemplate jdbc, Object sessions, Object users) throws Exception {
        Class<?> sessionClass = Class.forName("org.example.xyyx.service.PhoneRevealSessionService");
        Class<?> currentUserServiceClass = Class.forName("org.example.xyyx.service.CurrentUserService");
        return Class.forName("org.example.xyyx.service.TenantSystemSettingsService")
                .getConstructor(JdbcTemplate.class, sessionClass, currentUserServiceClass)
                .newInstance(jdbc, sessions, users);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }
}
