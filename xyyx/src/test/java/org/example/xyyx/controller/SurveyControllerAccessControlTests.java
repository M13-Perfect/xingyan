package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：process / delete / share 三个端点曾无鉴权无行过滤(任何登录用户可越权处理/删除/改可见性)。
 * 锁定修复后的形态，防止有人把校验改回去。沿用本项目的源码文本断言风格，仅断言 ASCII 片段。
 */
class SurveyControllerAccessControlTests {

    @Test
    void processDeleteShareEnforceServerSideAuthorization() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        )).replace("\r\n", "\n");
        String mapper = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "mapper", "SurveyMapper.java"
        )).replace("\r\n", "\n");

        // process：需带 jwt + requireUser，按 admin/staff 分流到租户/可见范围作用域方法，未命中 403。
        assertTrue(controller.contains("public String process(@AuthenticationPrincipal Jwt jwt"));
        assertTrue(controller.contains("updateAdminStatus(DEFAULT_TENANT_ID, id)"));
        assertTrue(controller.contains("updateStaffStatus(currentUser.username(), DEFAULT_TENANT_ID, id)"));

        // delete：管理员专属 + 租户作用域，命中 0 行报 404。
        assertTrue(controller.contains("public String delete(@AuthenticationPrincipal Jwt jwt"));
        assertTrue(controller.contains("deleteByIdTenant(DEFAULT_TENANT_ID, id)"));

        // share：管理员专属 + 可见性白名单 + 租户作用域。
        assertTrue(controller.contains("public String shareSurvey(@AuthenticationPrincipal Jwt jwt"));
        assertTrue(controller.contains("ALLOWED_VISIBILITY.contains(visibility)"));
        assertTrue(controller.contains("updateVisibilityTenant(DEFAULT_TENANT_ID, id, visibility, sharedUsers)"));

        // 每个失败路径都要抛访问控制错误码。
        assertTrue(controller.contains("SURVEY_ACCESS_DENIED"));
        assertTrue(controller.contains("requireAdmin(jwt)"));

        // Mapper 提供作用域方法。
        assertTrue(mapper.contains("int updateAdminStatus("));
        assertTrue(mapper.contains("int updateStaffStatus("));
        assertTrue(mapper.contains("int deleteByIdTenant("));
        assertTrue(mapper.contains("int updateVisibilityTenant("));

        // 旧的无作用域方法必须彻底删除，杜绝再次被误用。
        assertFalse(mapper.contains("int updateStatus(Long id)"));
        assertFalse(mapper.contains("int deleteById(Long id)"));
        assertFalse(mapper.contains("void updateVisibility("));
        assertFalse(controller.contains("surveyMapper.updateStatus(id)"));
        assertFalse(controller.contains("surveyMapper.deleteById(id)"));
        assertFalse(controller.contains("surveyMapper.updateVisibility(id"));
    }
}
