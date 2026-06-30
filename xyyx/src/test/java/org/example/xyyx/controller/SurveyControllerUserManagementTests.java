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
    void deleteUserRevokesWorkflowRoleRowsBeforeRemovingLegacyUser() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");

        assertTrue(controller.contains("@Transactional\n    public String deleteUser"));
        assertTrue(controller.contains("UPDATE user_role SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL"));
        assertTrue(controller.contains("DELETE FROM user WHERE id = ?"));
    }
}
