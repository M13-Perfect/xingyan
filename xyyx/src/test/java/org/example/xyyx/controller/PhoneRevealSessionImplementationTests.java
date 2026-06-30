package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhoneRevealSessionImplementationTests {

    @Test
    void controllersUseSystemSettingsInsteadOfEmployeePolicy() throws Exception {
        String controller = read("src/main/java/org/example/xyyx/controller/SurveyController.java");
        String displayService = read("src/main/java/org/example/xyyx/service/SurveyPhoneDisplayService.java");

        assertTrue(controller.contains("/admin/system-settings"));
        assertTrue(controller.contains("/app-settings"));
        assertTrue(controller.contains("TenantSystemSettingsService"));
        assertTrue(displayService.contains("GlobalPhoneDisplayPolicyMode.MASKED_ONLY"));
        assertTrue(displayService.contains("GlobalPhoneDisplayPolicyMode.CLICK_TO_SESSION_VISIBLE"));
        assertFalse(controller.contains("EmployeePhoneDisplayPolicyService"));
        assertFalse(controller.contains("admin/users/{userId}/phone-display-policy"));
        assertFalse(controller.contains("phone-display-policy/batch"));
        assertFalse(displayService.contains("EmployeePhoneDisplayPolicyService"));
    }

    @Test
    void sessionRevealUsesServerSideLoginSessionState() throws Exception {
        String currentUserService = read("src/main/java/org/example/xyyx/service/CurrentUserService.java");
        String sessionService = read("src/main/java/org/example/xyyx/service/PhoneRevealSessionService.java");

        assertTrue(currentUserService.contains("loginSessionId"));
        assertTrue(currentUserService.contains("\"sid\""));
        assertTrue(currentUserService.contains("\"sessionId\""));
        assertTrue(currentUserService.contains("jwt.getId()"));
        assertTrue(currentUserService.contains("jwt.getTokenValue()"));
        assertTrue(sessionService.contains("phone_reveal_login_session"));
        assertTrue(sessionService.contains("PHONE_SESSION_VISIBILITY_ACTIVATED"));
        assertFalse(sessionService.contains("PHONE_VIEW_FULL_SESSION"));
        assertFalse(sessionService.contains("tenantId + userId"));
    }

    @Test
    void surveyListOnlyRevealsCurrentPageAndDegradesMissingPrivacyFields() throws Exception {
        String displayService = read("src/main/java/org/example/xyyx/service/SurveyPhoneDisplayService.java");
        String mapper = read("src/main/java/org/example/xyyx/mapper/SurveyMapper.java");

        assertTrue(displayService.contains("PHONE_REVEAL_SESSION_BATCH"));
        assertTrue(displayService.contains("PHONE_PRIVACY_NOT_READY"));
        assertTrue(displayService.contains("phoneDisplay"));
        assertTrue(displayService.contains("phoneRevealed"));
        assertTrue(displayService.contains("phoneDisplayPolicy"));
        assertTrue(displayService.contains("selectAdminPhoneSecretsByIds"));
        assertTrue(displayService.contains("survey.getPhoneMask() != null"));
        assertTrue(displayService.contains("!survey.getPhoneMask().isBlank()"));
        assertTrue(mapper.contains("<foreach"));
        assertTrue(mapper.contains("tenant_id = #{tenantId}"));
        assertFalse(mapper.contains("phone LIKE"));
    }

    @Test
    void missingPhoneMaskDoesNotTryDecryptAndMarksNotReady() throws Exception {
        Fixture fixture = fixture();
        Object pageRow = survey(1L, null);
        Object secret = privacyReadySurvey(1L, " ");
        when(fixture.selectAdminPhoneSecretsByIds.invoke(fixture.mapper, "default", List.of(1L))).thenReturn(List.of(secret));

        List<Map<String, Object>> rows = invokeRows(fixture, List.of(pageRow));

        assertEquals("PHONE_PRIVACY_NOT_READY", rows.get(0).get("phoneRevealStatus"));
        assertFalse((Boolean) rows.get(0).get("phoneRevealed"));
        verifyNoInteractions(fixture.privacyService);
    }

    @Test
    void maskedOnlyNeverShowsRevealButton() throws Exception {
        Fixture fixture = fixture(maskedSettings());
        Object pageRow = survey(2L, "138****0000");

        List<Map<String, Object>> rows = invokeRows(fixture, List.of(pageRow));

        assertEquals("MASKED_ONLY", rows.get(0).get("phoneDisplayPolicy").toString());
        assertFalse((Boolean) rows.get(0).get("canRevealPhone"));
        assertFalse((Boolean) rows.get(0).get("phoneRevealed"));
        verifyNoInteractions(fixture.privacyService);
    }

    @Test
    void singleOrderTimedRevealsOnlyGrantedOrders() throws Exception {
        Fixture fixture = fixture(singleOrderTimedSettings());
        Object grantedRow = survey(10L, "138****1111");
        Object otherRow = survey(11L, "138****2222");
        Object grantedSecret = privacyReadySurvey(10L, "138****1111");
        long expiry = 9_999_999_999_999L;
        when(fixture.selectAdminPhoneSecretsByIds.invoke(fixture.mapper, "default", List.of(10L, 11L)))
                .thenReturn(List.of(grantedSecret));
        when(fixture.decryptPhone.invoke(fixture.privacyService, grantedSecret)).thenReturn("13800001111");

        List<Map<String, Object>> rows = invokeRowsOff(fixture, List.of(grantedRow, otherRow), Map.of(10L, expiry));
        Map<String, Object> revealed = byId(rows, 10L);
        Map<String, Object> masked = byId(rows, 11L);

        assertEquals("REVEALED", revealed.get("phoneRevealStatus"));
        assertTrue((Boolean) revealed.get("phoneRevealed"));
        assertEquals("13800001111", revealed.get("phoneDisplay"));
        assertFalse((Boolean) revealed.get("canRevealPhone"));
        assertEquals(expiry, ((Number) revealed.get("phoneRevealExpiresAt")).longValue());
        assertEquals("MASKED", masked.get("phoneRevealStatus"));
        assertFalse((Boolean) masked.get("phoneRevealed"));
        assertTrue((Boolean) masked.get("canRevealPhone"));
    }

    @Test
    void singleOrderTimedWithNoGrantKeepsEveryRowMaskedAndClickable() throws Exception {
        Fixture fixture = fixture(singleOrderTimedSettings());
        Object pageRow = survey(12L, "138****3333");

        List<Map<String, Object>> rows = invokeRowsOff(fixture, List.of(pageRow), Map.of());

        assertEquals("MASKED", rows.get(0).get("phoneRevealStatus"));
        assertFalse((Boolean) rows.get(0).get("phoneRevealed"));
        assertTrue((Boolean) rows.get(0).get("canRevealPhone"));
        verifyNoInteractions(fixture.privacyService);
    }

    @Test
    void singleRevealAllowsEmptyBodyAndUsesSurveyRoute() throws Exception {
        String controller = read("src/main/java/org/example/xyyx/controller/SurveyController.java");
        String sessionService = read("src/main/java/org/example/xyyx/service/PhoneRevealSessionService.java");

        assertTrue(controller.contains("@RequestBody(required = false)"));
        assertTrue(controller.contains("/surveys/{surveyId}/phone/reveal"));
        assertTrue(controller.contains("业务员点击查看完整手机号"));
        assertTrue(controller.contains("PHONE_PRIVACY_NOT_READY"));
        assertTrue(controller.contains("phoneRevealSessionService.activate"));
        assertFalse(controller.contains("response.put(\"phone\","));
        assertTrue(sessionService.contains("PHONE_SESSION_VISIBILITY_ACTIVATED"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static Fixture fixture() throws Exception {
        return fixture(clickSettings());
    }

    private static Fixture fixture(Object settings) throws Exception {
        Class<?> surveyMapper = Class.forName("org.example.xyyx.mapper.SurveyMapper");
        Class<?> phonePrivacyService = Class.forName("org.example.xyyx.service.PhonePrivacyService");
        Class<?> rateLimiter = Class.forName("org.example.xyyx.service.InMemoryRateLimiter");
        Class<?> displayService = Class.forName("org.example.xyyx.service.SurveyPhoneDisplayService");
        Class<?> currentUser = Class.forName("org.example.xyyx.service.CurrentUserService$CurrentUser");
        Class<?> request = Class.forName("jakarta.servlet.http.HttpServletRequest");
        Class<?> jdbc = Class.forName("org.springframework.jdbc.core.JdbcTemplate");
        Class<?> settingsClass = Class.forName("org.example.xyyx.service.TenantSystemSettingsService$TenantSystemSettings");
        Object mapper = mock(surveyMapper);
        Object privacy = mock(phonePrivacyService);
        Object jdbcTemplate = mock(jdbc);
        Object service = displayService
                .getConstructor(surveyMapper, phonePrivacyService, jdbc, rateLimiter)
                .newInstance(mapper, privacy, jdbcTemplate, rateLimiter.getConstructor().newInstance());
        return new Fixture(
                mapper,
                privacy,
                service,
                currentUser.getConstructor(String.class, String.class).newInstance("admin", "admin"),
                settings,
                surveyMapper.getMethod("selectAdminPhoneSecretsByIds", String.class, List.class),
                phonePrivacyService.getMethod("decryptPhone", surveyClass()),
                displayService.getMethod("toResponseRows", List.class, currentUser, Long.class, settingsClass, boolean.class, Map.class, String.class, String.class, int.class, int.class, request)
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> invokeRows(Fixture fixture, List<Object> page) throws Exception {
        return (List<Map<String, Object>>) fixture.toResponseRows.invoke(
                fixture.service, page, fixture.user, 1L, fixture.settings, true, null, "default", null, 1, 20, null);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object singleOrderTimedSettings() throws Exception {
        Class<?> settings = Class.forName("org.example.xyyx.service.TenantSystemSettingsService$TenantSystemSettings");
        Class<?> mode = Class.forName("org.example.xyyx.service.GlobalPhoneDisplayPolicyMode");
        return settings.getConstructor(String.class, mode, int.class, Object.class)
                .newInstance("default", Enum.valueOf((Class<Enum>) mode, "SINGLE_ORDER_TIMED_REVEAL"), 20, null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> invokeRowsOff(Fixture fixture, List<Object> page, Map<Long, Long> granted) throws Exception {
        return (List<Map<String, Object>>) fixture.toResponseRows.invoke(
                fixture.service, page, fixture.user, 1L, fixture.settings, false, granted, "default", null, 1, 20, null);
    }

    private static Map<String, Object> byId(List<Map<String, Object>> rows, long id) {
        return rows.stream()
                .filter(r -> ((Number) r.get("id")).longValue() == id)
                .findFirst()
                .orElseThrow();
    }

    private static Object survey(Long id, String phoneMask) throws Exception {
        Object survey = surveyClass().getConstructor().newInstance();
        set(survey, "setId", Long.class, id);
        set(survey, "setTenantId", String.class, "default");
        set(survey, "setCustomerUuid", String.class, "customer-" + id);
        set(survey, "setPhoneMask", String.class, phoneMask);
        return survey;
    }

    private static Object privacyReadySurvey(Long id, String phoneMask) throws Exception {
        Object survey = survey(id, phoneMask);
        set(survey, "setPhoneCiphertext", byte[].class, new byte[]{1});
        set(survey, "setPhoneIv", byte[].class, new byte[]{2});
        set(survey, "setPhoneTag", byte[].class, new byte[]{3});
        set(survey, "setPhoneEncKeyVersion", String.class, "v1");
        return survey;
    }

    private static void set(Object target, String method, Class<?> type, Object value) throws Exception {
        surveyClass().getMethod(method, type).invoke(target, value);
    }

    private static Class<?> surveyClass() throws Exception {
        return Class.forName("org.example.xyyx.entity.Survey");
    }

    private record Fixture(
            Object mapper,
            Object privacyService,
            Object service,
            Object user,
            Object settings,
            Method selectAdminPhoneSecretsByIds,
            Method decryptPhone,
            Method toResponseRows
    ) {
    }
}
