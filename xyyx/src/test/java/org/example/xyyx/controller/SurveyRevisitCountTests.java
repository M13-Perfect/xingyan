package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-contract tests for the回访 (revisit) features, mirroring the assertion style of
 * {@link SurveyReminderDateTests}. They lock the frozen API contract that the前端 depends on:
 *   1. GET /api/surveys 的可选 revisit=due 过滤(列表 + 总数,admin/staff 两条路径)。
 *   2. GET /api/surveys/revisit-count 的 today/overdue/due 语义与范围隔离。
 *
 * The behavioural intent each assertion encodes:
 *   - revisit-count: 一条今天到期未处理 → today=1;一条已逾期未处理 → overdue=1;
 *     一条未来未处理、一条已逾期但已处理 → 都不计入;due = today + overdue = 2。
 *   - staff 范围隔离:revisit-count 与列表都对 staff 追加 STAFF_SCOPE,别人 private 的不可见。
 *   - revisit=due 列表:只返回 next_survey_date < 明天零点(即今天到期 + 已逾期)的记录。
 */
class SurveyRevisitCountTests {

    private static String controllerSource() throws Exception {
        return Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");
    }

    private static String mapperSource() throws Exception {
        return Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "mapper", "SurveyMapper.java"
        )).replace("\r\n", "\n");
    }

    /**
     * Reconstructs the effective SQL text from Java源码 so assertions don't depend on line wrapping:
     * joins adjacent string literals ("...\" + \"...") and collapses runs of whitespace to a single
     * space. The constant-concat form ("..." + STAFF_SCOPE) is intentionally left intact, because the
     * identifier after '+' is not an opening quote, so the literal-join regex does not match it.
     */
    private static String flatten(String source) {
        String joined = source.replaceAll("\"\\s*\\+\\s*\"", "");
        return joined.replaceAll("\\s+", " ");
    }

    @Test
    void revisitCountEndpointReportsTodayOverdueAndDueWithCorrectScope() throws Exception {
        String controller = controllerSource();
        String mapper = mapperSource();

        // Endpoint shape: same auth/tenant as getSurveys.
        assertTrue(controller.contains("@GetMapping(\"/surveys/revisit-count\")"));
        assertTrue(controller.contains("public Map<String, Object> getRevisitCount(@AuthenticationPrincipal Jwt jwt)"));
        assertTrue(controller.contains("currentUserService.requireUser(jwt)"));

        // Boundaries: todayStart = 今天零点, tomorrowStart = todayStart + 1 天。
        assertTrue(controller.contains("LocalDateTime todayStart = LocalDate.now().atStartOfDay()"));
        assertTrue(controller.contains("LocalDateTime tomorrowStart = todayStart.plusDays(1)"));

        // Admin sees whole tenant; staff goes through the username-scoped variants.
        assertTrue(controller.contains("countRevisitTodayAdmin(DEFAULT_TENANT_ID, todayStart, tomorrowStart)"));
        assertTrue(controller.contains("countRevisitOverdueAdmin(DEFAULT_TENANT_ID, todayStart)"));
        assertTrue(controller.contains("countRevisitTodayStaff(currentUser.username(), DEFAULT_TENANT_ID, todayStart, tomorrowStart)"));
        assertTrue(controller.contains("countRevisitOverdueStaff(currentUser.username(), DEFAULT_TENANT_ID, todayStart)"));

        // Response field names are frozen: today / overdue / due, with due = today + overdue.
        assertTrue(controller.contains("response.put(\"today\", today)"));
        assertTrue(controller.contains("response.put(\"overdue\", overdue)"));
        assertTrue(controller.contains("response.put(\"due\", today + overdue)"));

        // Counting SQL: only 未处理 counts; today is the half-open window [start, end); overdue is < end.
        // 已处理 records and future records are therefore excluded, so today=1 / overdue=1 / due=2 for the
        // documented data set. Whitespace/line-wrapping is normalized so the checks stay robust.
        String mapperFlat = flatten(mapper);
        assertTrue(mapper.contains("int countRevisitTodayAdmin("));
        assertTrue(mapper.contains("int countRevisitOverdueAdmin("));
        assertTrue(mapperFlat.contains("status = '未处理' AND next_survey_date >= #{start} AND next_survey_date &lt; #{end}"));
        // Overdue query: still gated on 未处理, and everything before #{end} (= todayStart) counts.
        assertTrue(mapperFlat.contains("countRevisitOverdueAdmin"));
        assertTrue(mapperFlat.contains("WHERE tenant_id = #{tenantId} AND status = '未处理' AND next_survey_date &lt; #{end}"));
    }

    @Test
    void revisitCountStaffVariantsAreScopedToVisibleRecords() throws Exception {
        String mapper = mapperSource();

        // Staff counting methods must append STAFF_SCOPE so别人 private 的记录不计入。
        String mapperFlat = flatten(mapper);
        assertTrue(mapper.contains("int countRevisitTodayStaff("));
        assertTrue(mapper.contains("int countRevisitOverdueStaff("));
        assertTrue(mapper.contains("String STAFF_SCOPE = \"(owner = #{username} OR visibility = 'PUBLIC' OR FIND_IN_SET(#{username}, shared_users) > 0)\""));
        // Both staff variants are scripted COUNT(*) queries that end with: AND " + STAFF_SCOPE.
        assertTrue(mapperFlat.contains("AND next_survey_date >= #{start} AND next_survey_date &lt; #{end} AND \" + STAFF_SCOPE"));
        assertTrue(mapperFlat.contains("AND next_survey_date &lt; #{end} AND \" + STAFF_SCOPE"));
    }

    @Test
    void revisitDueListFilterRestrictsToTodayAndOverdueForBothRoles() throws Exception {
        String controller = controllerSource();
        String mapper = mapperSource();

        // Controller maps revisit=="due" to dueBefore = 明天零点 (含今天到期 + 已逾期), else null.
        assertTrue(controller.contains("@RequestParam(required = false) String revisit"));
        assertTrue(controller.contains("LocalDateTime dueBefore = \"due\".equals(revisit) ? LocalDate.now().plusDays(1).atStartOfDay() : null"));

        // dueBefore threads through both列表 and总数, on admin and staff paths.
        assertTrue(controller.contains("selectAdminPaged(DEFAULT_TENANT_ID, queryStatus, search.keyword(), search.phoneHash(), search.phoneSuffix4Hash(), city, dueBefore, pageSize, offset)"));
        assertTrue(controller.contains("countAdmin(DEFAULT_TENANT_ID, queryStatus, search.keyword(), search.phoneHash(), search.phoneSuffix4Hash(), city, dueBefore)"));
        assertTrue(controller.contains("selectStaffPaged(currentUser.username(), DEFAULT_TENANT_ID, queryStatus, search.keyword(), search.phoneHash(), search.phoneSuffix4Hash(), city, dueBefore, pageSize, offset)"));
        assertTrue(controller.contains("countStaff(currentUser.username(), DEFAULT_TENANT_ID, queryStatus, search.keyword(), search.phoneHash(), search.phoneSuffix4Hash(), city, dueBefore)"));

        // Mapper applies the filter only when dueBefore != null, so default behaviour is unchanged.
        // The condition keeps records whose next_survey_date < 明天零点 (i.e. <= 今天), exactly the
        // due-list semantics, on all four query/count methods.
        assertTrue(mapper.contains("<if test='dueBefore != null'> AND next_survey_date IS NOT NULL AND next_survey_date &lt; #{dueBefore} </if>"));
        assertTrue(mapper.contains("@Param(\"dueBefore\") LocalDateTime dueBefore"));
    }
}
