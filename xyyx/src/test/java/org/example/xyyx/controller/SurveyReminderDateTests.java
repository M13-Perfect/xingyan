package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyReminderDateTests {

    @Test
    void updateDateUsesCurrentUserScopeAndReportsResult() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");
        String mapper = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "mapper", "SurveyMapper.java"
        )).replace("\r\n", "\n");

        assertTrue(controller.contains("public Map<String, Object> updateDate(@AuthenticationPrincipal Jwt jwt"));
        assertTrue(controller.contains("currentUserService.requireUser(jwt)"));
        assertTrue(controller.contains("payload == null ? null : payload.get(\"date\")"));
        assertTrue(controller.contains("DATE_REQUIRED"));
        assertTrue(controller.contains("DATE_INVALID"));
        assertTrue(controller.contains("DATE_IN_PAST"));
        assertTrue(controller.contains("updateAdminNextDate(DEFAULT_TENANT_ID, id, nextSurveyDate)"));
        assertTrue(controller.contains("updateStaffNextDate(currentUser.username(), DEFAULT_TENANT_ID, id, nextSurveyDate)"));
        assertTrue(controller.contains("SURVEY_ACCESS_DENIED"));
        assertTrue(controller.contains("response.put(\"nextSurveyDate\", date.toString())"));
        assertTrue(controller.contains("response.put(\"due\", !date.isAfter(LocalDate.now()))"));

        assertTrue(mapper.contains("int updateAdminNextDate("));
        assertTrue(mapper.contains("WHERE tenant_id = #{tenantId} AND id = #{id}"));
        assertTrue(mapper.contains("int updateStaffNextDate("));
        assertTrue(mapper.contains("String STAFF_SCOPE = \"(owner = #{username} OR visibility = 'PUBLIC' OR FIND_IN_SET(#{username}, shared_users) > 0)\""));
        assertTrue(mapper.contains("WHERE tenant_id = #{tenantId} AND id = #{id} AND \" + STAFF_SCOPE"));
    }
}
