package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyControllerUserManagementTests {

    @Test
    void addUserKeepsWorkflowRoleTableInSync() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");

        assertTrue(controller.contains("@Transactional\n    public String addUser"));
        assertTrue(controller.contains("INSERT INTO user (username, password, role) VALUES (?, ?, ?)"));
        assertTrue(controller.contains("INSERT INTO user_role (user_id, role_id, assigned_by_user_id, created_at, revoked_at)"));
        assertTrue(controller.contains("SELECT ?, r.id, NULL, CURRENT_TIMESTAMP, NULL FROM sys_role r WHERE r.code = ?"));
        assertTrue(controller.contains("Missing STAFF role seed"));
    }

    @Test
    void addUserAcceptsOptionalNicknameAndDefaultsToUsernameForCasdoorDisplayName() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");

        assertTrue(controller.contains("String nickname = payload.get(\"nickname\") == null ? null : payload.get(\"nickname\").trim();"));
        assertTrue(controller.contains("if (nickname != null && nickname.length() > 20) return \"昵称格式不正确：最长 20 字\";"));
        assertTrue(controller.contains("String displayName = (nickname == null || nickname.isEmpty()) ? username : nickname;"));
        assertTrue(controller.contains("casdoorAdminService.createUser(username, plainPassword, displayName);"));

        String casdoorService = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "service", "CasdoorAdminService.java"
        )).replace("\r\n", "\n");
        assertTrue(casdoorService.contains("public void createUser(String username, String plainPassword, String displayName)"));
        assertTrue(casdoorService.contains("body.put(\"displayName\", displayName);"));
    }

    @Test
    void deleteUserRevokesWorkflowRoleRowsBeforeRemovingLegacyUser() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");

        assertTrue(controller.contains("@Transactional\n    public String deleteUser"));
        assertTrue(controller.contains("UPDATE user_role SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL"));
        assertTrue(controller.contains("DELETE FROM user WHERE id = ?"));
    }

    @Test
    void adminPasswordResetSyncsCasdoorSoRealLoginPasswordActuallyChanges() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");

        assertTrue(controller.contains("@Transactional\n    public String updateUserPassword"));
        assertTrue(controller.contains("casdoorAdminService.updateUserPassword(username, plainPassword);"));
    }

    @Test
    void selfServicePasswordChangeVerifiesOldPasswordBeforeSyncingCasdoor() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");

        assertTrue(controller.contains("@PutMapping(\"/me/password\")"));
        assertTrue(controller.contains("passwordEncoder.matches(currentPlainPassword, currentHash)"));
        assertTrue(controller.contains("casdoorAdminService.updateUserPassword(currentUser.username(), newPlainPassword);"));
    }

    @Test
    void selfServiceProfileUpdateWritesOnlyToCasdoorNotLocalUserTable() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");

        assertTrue(controller.contains("@PutMapping(\"/me/profile\")"));
        assertTrue(controller.contains("casdoorAdminService.updateUserDisplayName(currentUser.username(), displayName);"));
        assertTrue(!controller.contains("UPDATE user SET nickname"));
    }

    @Test
    void getUsersEndpointIncludesPerEmployeePendingAndOverdueCountsScopedToOwner() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");
        String mapper = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "mapper", "SurveyMapper.java"
        )).replace("\r\n", "\n");

        // getUsers 用同一套 revisitDeadlineDays/overdueCutoff 语义(与 getRevisitCount 一致),
        // 但计数按 owner=该员工 精确统计,不含公开/分享记录,避免同一条记录被多名员工重复计入。
        assertTrue(controller.contains(
                "int revisitDeadlineDays = tenantSystemSettingsService.getEffectiveSettings(DEFAULT_TENANT_ID).revisitDeadlineDays();"));
        assertTrue(controller.contains("LocalDateTime overdueCutoff = LocalDate.now().atStartOfDay().minusDays(revisitDeadlineDays);"));
        assertTrue(controller.contains("int overdue = surveyMapper.countOverdueByOwner(DEFAULT_TENANT_ID, username, overdueCutoff);"));
        assertTrue(controller.contains("int total = surveyMapper.countPendingByOwner(DEFAULT_TENANT_ID, username);"));
        // 未处理(pendingCount) 与已逾期(overdueCount) 互斥、相加等于该员工全部未处理记录。
        assertTrue(controller.contains("u.put(\"pendingCount\", total - overdue);"));
        assertTrue(controller.contains("u.put(\"overdueCount\", overdue);"));

        assertTrue(mapper.contains(
                "SELECT COUNT(*) FROM survey WHERE tenant_id = #{tenantId} AND status = '未处理' AND owner = #{username}"));
        assertTrue(mapper.contains("int countPendingByOwner(@Param(\"tenantId\") String tenantId, @Param(\"username\") String username);"));
        assertTrue(mapper.contains(
                "int countOverdueByOwner(@Param(\"tenantId\") String tenantId, @Param(\"username\") String username, @Param(\"end\") LocalDateTime end);"));
    }

    @Test
    void adminSurveyListEnrichesRowsWithCasdoorNicknameBestEffort() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");
        String casdoorService = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "service", "CasdoorAdminService.java"
        )).replace("\r\n", "\n");

        // 只有管理员工作台会触发 Casdoor 昵称查询;员工自己的列表不受影响、不多打一次 Casdoor 请求。
        assertTrue(controller.contains("if (currentUser.isAdmin()) {"));
        assertTrue(controller.contains("Map<String, String> nicknames = casdoorAdminService.listDisplayNames();"));
        assertTrue(controller.contains("row.put(\"ownerNickname\", nicknames.getOrDefault(owner, owner));"));

        // 批量昵称查询尽力而为:请求/解析失败一律退回空 Map,前端会自动 fallback 回账号名,不影响列表可用性。
        assertTrue(casdoorService.contains("public Map<String, String> listDisplayNames()"));
        assertTrue(casdoorService.contains(".path(\"/api/get-users\")"));
        assertTrue(casdoorService.contains(".queryParam(\"owner\", organization)"));
        assertTrue(casdoorService.contains("} catch (Exception e) {\n            return Map.of();\n        }"));
        assertTrue(casdoorService.contains("if (!(data instanceof List<?> rawList)) {\n            return Map.of();\n        }"));
    }
}
