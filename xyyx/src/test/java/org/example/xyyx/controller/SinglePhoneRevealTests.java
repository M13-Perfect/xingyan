package org.example.xyyx.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SinglePhoneRevealTests {

    @Test
    void revealRejectsBadIvLengthBeforeDecrypt() throws Exception {
        Fixture fixture = fixture();
        Object survey = fullSurvey();
        set(survey, "setPhoneIv", byte[].class, new byte[11]);
        when(fixture.selectAdminPhoneSecretsById.invoke(fixture.mapper, 7L, "default")).thenReturn(survey);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class, fixture::reveal);

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        assertEquals("PHONE_PRIVACY_NOT_READY", thrown.getReason());
        verifyNoInteractions(fixture.privacy);
    }

    @Test
    void revealRejectsBadTagLengthBeforeDecrypt() throws Exception {
        Fixture fixture = fixture();
        Object survey = fullSurvey();
        set(survey, "setPhoneTag", byte[].class, new byte[15]);
        when(fixture.selectAdminPhoneSecretsById.invoke(fixture.mapper, 7L, "default")).thenReturn(survey);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class, fixture::reveal);

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        assertEquals("PHONE_PRIVACY_NOT_READY", thrown.getReason());
        verifyNoInteractions(fixture.privacy);
    }

    @Test
    void revealDecryptFailureReturnsStructuredJsonWithRequestId() throws Exception {
        Fixture fixture = fixture();
        Object survey = fullSurvey();
        when(fixture.selectAdminPhoneSecretsById.invoke(fixture.mapper, 7L, "default")).thenReturn(survey);
        Object exception = Class.forName("org.example.xyyx.service.PhonePrivacyException")
                .getConstructor(String.class).newInstance("PHONE_DECRYPT_FAILED");
        when(fixture.decryptPhone.invoke(fixture.privacy, survey)).thenThrow((Throwable) exception);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class, fixture::reveal);
        Object handler = Class.forName("org.example.xyyx.config.GlobalExceptionHandler").getConstructor().newInstance();
        Method handleStatus = handler.getClass().getMethod("handleStatus", ResponseStatusException.class, HttpServletRequest.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) handleStatus.invoke(handler, thrown, fixture.request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("PHONE_DECRYPT_FAILED", response.getBody().get("code"));
        assertEquals("手机号解密失败，请联系管理员", response.getBody().get("message"));
        assertEquals("req-1", response.getBody().get("requestId"));
    }

    @Test
    void auditFailureReturnsStructuredAuditUnavailable() throws Exception {
        Fixture fixture = fixture();
        Object survey = fullSurvey();
        when(fixture.selectAdminPhoneSecretsById.invoke(fixture.mapper, 7L, "default")).thenReturn(survey);
        when(fixture.decryptPhone.invoke(fixture.privacy, survey)).thenReturn("+8613812345678");
        doThrow(new RuntimeException("audit unavailable")).when(fixture.jdbc).update(anyString(), any(Object[].class));

        Exception thrown = assertThrows(Exception.class, fixture::reveal);
        Object handler = Class.forName("org.example.xyyx.config.GlobalExceptionHandler").getConstructor().newInstance();
        Method handleApi = handler.getClass().getMethod("handleApi", thrown.getClass(), HttpServletRequest.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) handleApi.invoke(handler, thrown, fixture.request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("AUDIT_LOG_UNAVAILABLE", response.getBody().get("code"));
        assertEquals("个人信息访问审计不可用，请联系管理员", response.getBody().get("message"));
        assertEquals("req-1", response.getBody().get("requestId"));
    }

    @Test
    void maskedOnlyRejectsRevealBeforeDecrypt() throws Exception {
        Fixture fixture = fixture(maskedSettings());
        Object survey = fullSurvey();
        when(fixture.selectAdminPhoneSecretsById.invoke(fixture.mapper, 7L, "default")).thenReturn(survey);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class, fixture::reveal);

        assertEquals(HttpStatus.FORBIDDEN, thrown.getStatusCode());
        assertEquals("PHONE_VIEW_DENIED", thrown.getReason());
        verifyNoInteractions(fixture.privacy);
    }

    @Test
    void successfulRevealActivatesCurrentLoginSession() throws Exception {
        Fixture fixture = fixture();
        Object survey = fullSurvey();
        when(fixture.selectAdminPhoneSecretsById.invoke(fixture.mapper, 7L, "default")).thenReturn(survey);
        when(fixture.decryptPhone.invoke(fixture.privacy, survey)).thenReturn("+8613812345678");

        Map<String, Object> response = fixture.reveal();

        assertEquals(true, response.get("sessionActivated"));
        assertEquals("SESSION_ACTIVATED", response.get("phoneRevealStatus"));
        fixture.sessionService.getClass()
                .getMethod("activate", String.class, fixture.user.getClass(), Long.class, Jwt.class, HttpServletRequest.class)
                .invoke(verify(fixture.sessionService), "default", fixture.user, 42L, fixture.jwt, fixture.request);
    }

    private static Fixture fixture() throws Exception {
        return fixture(clickSettings());
    }

    private static Fixture fixture(Object settings) throws Exception {
        Class<?> controllerClass = Class.forName("org.example.xyyx.controller.SurveyController");
        Class<?> mapperClass = Class.forName("org.example.xyyx.mapper.SurveyMapper");
        Class<?> currentUserServiceClass = Class.forName("org.example.xyyx.service.CurrentUserService");
        Class<?> currentUserClass = Class.forName("org.example.xyyx.service.CurrentUserService$CurrentUser");
        Class<?> privacyClass = Class.forName("org.example.xyyx.service.PhonePrivacyService");
        Class<?> limiterClass = Class.forName("org.example.xyyx.service.InMemoryRateLimiter");
        Class<?> settingsServiceClass = Class.forName("org.example.xyyx.service.TenantSystemSettingsService");
        Class<?> sessionServiceClass = Class.forName("org.example.xyyx.service.PhoneRevealSessionService");

        Object controller = controllerClass.getConstructor().newInstance();
        Object mapper = mock(mapperClass);
        Object users = mock(currentUserServiceClass);
        Object privacy = mock(privacyClass);
        Object settingsService = mock(settingsServiceClass);
        Object sessionService = mock(sessionServiceClass);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        Object user = currentUserClass.getConstructor(String.class, String.class).newInstance("alice", "admin");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("jti", "login-1")
                .issuedAt(Instant.parse("2026-06-30T00:00:00Z"))
                .expiresAt(Instant.parse("2026-06-30T01:00:00Z"))
                .build();

        ReflectionTestUtils.setField(controller, "surveyMapper", mapper);
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(controller, "currentUserService", users);
        ReflectionTestUtils.setField(controller, "phonePrivacyService", privacy);
        ReflectionTestUtils.setField(controller, "tenantSystemSettingsService", settingsService);
        ReflectionTestUtils.setField(controller, "phoneRevealSessionService", sessionService);
        ReflectionTestUtils.setField(controller, "rateLimiter", mock(limiterClass));

        when(currentUserServiceClass.getMethod("requireUser", Jwt.class).invoke(users, jwt)).thenReturn(user);
        when(currentUserServiceClass.getMethod("userId", currentUserClass).invoke(users, user)).thenReturn(42L);
        when(settingsServiceClass.getMethod("getEffectiveSettings", String.class).invoke(settingsService, "default"))
                .thenReturn(settings);
        when(request.getAttribute("requestId")).thenReturn("req-1");

        return new Fixture(
                controller,
                mapper,
                privacy,
                jdbc,
                sessionService,
                user,
                jwt,
                request,
                mapperClass.getMethod("selectAdminPhoneSecretsById", Long.class, String.class),
                privacyClass.getMethod("decryptPhone", surveyClass()),
                controllerClass.getMethod("revealCustomerPhone", Jwt.class, Long.class, Map.class, HttpServletRequest.class)
        );
    }

    private static Object fullSurvey() throws Exception {
        Object survey = surveyClass().getConstructor().newInstance();
        set(survey, "setId", Long.class, 7L);
        set(survey, "setTenantId", String.class, "default");
        set(survey, "setCustomerUuid", String.class, "customer-7");
        set(survey, "setPhoneMask", String.class, "138****5678");
        set(survey, "setPhoneCiphertext", byte[].class, new byte[]{1, 2, 3});
        set(survey, "setPhoneIv", byte[].class, new byte[12]);
        set(survey, "setPhoneTag", byte[].class, new byte[16]);
        set(survey, "setPhoneEncKeyVersion", String.class, "v1");
        return survey;
    }

    private static void set(Object target, String method, Class<?> type, Object value) throws Exception {
        surveyClass().getMethod(method, type).invoke(target, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object clickSettings() throws Exception {
        Class<?> settings = Class.forName("org.example.xyyx.service.TenantSystemSettingsService$TenantSystemSettings");
        Class<?> mode = Class.forName("org.example.xyyx.service.GlobalPhoneDisplayPolicyMode");
        return settings.getConstructor(String.class, mode, int.class, Object.class)
                .newInstance("default", Enum.valueOf((Class<Enum>) mode, "CLICK_TO_SESSION_VISIBLE"), 20, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object maskedSettings() throws Exception {
        Class<?> settings = Class.forName("org.example.xyyx.service.TenantSystemSettingsService$TenantSystemSettings");
        Class<?> mode = Class.forName("org.example.xyyx.service.GlobalPhoneDisplayPolicyMode");
        return settings.getConstructor(String.class, mode, int.class, Object.class)
                .newInstance("default", Enum.valueOf((Class<Enum>) mode, "MASKED_ONLY"), 20, null);
    }

    private static Class<?> surveyClass() throws Exception {
        return Class.forName("org.example.xyyx.entity.Survey");
    }

    private record Fixture(
            Object controller,
            Object mapper,
            Object privacy,
            JdbcTemplate jdbc,
            Object sessionService,
            Object user,
            Jwt jwt,
            HttpServletRequest request,
            Method selectAdminPhoneSecretsById,
            Method decryptPhone,
            Method revealCustomerPhone
    ) {
        Map<String, Object> reveal() throws Exception {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = (Map<String, Object>) revealCustomerPhone.invoke(controller, jwt, 7L, Map.of(), request);
                return response;
            } catch (InvocationTargetException e) {
                throw (Exception) e.getCause();
            }
        }
    }
}
