package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyPhonePrivacyTests {

    @Test
    void surveyWritePathUsesPhonePrivacyFieldsInsteadOfLegacyPlainPhone() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        ));
        String mapper = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "mapper", "SurveyMapper.java"
        ));

        assertTrue(controller.contains("PhonePrivacyService"));
        assertTrue(controller.contains("normalizePhone"));
        assertTrue(controller.contains("encryptPhone"));
        assertTrue(controller.contains("buildPhoneHash"));
        assertTrue(controller.contains("catch (PhonePrivacyException e)"));
        assertTrue(controller.contains("res.put(\"code\", e.code())"));
        assertTrue(controller.contains("res.put(\"message\", e.code())"));
        assertFalse(controller.contains("s.setPhone(params.get(\"phone\")"));

        assertTrue(mapper.contains("phone_ciphertext"));
        assertTrue(mapper.contains("phone_hash"));
        assertFalse(mapper.contains("INSERT INTO survey(name, phone,"));
    }

    @Test
    void revealEndpointRequiresReasonPermissionAndAuditLog() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        ));
        String currentUserService = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "service", "CurrentUserService.java"
        ));

        assertTrue(controller.contains("/surveys/{surveyId}/phone/reveal"));
        assertTrue(controller.contains("@RequestBody(required = false)"));
        assertTrue(controller.contains("PHONE_VIEW_FULL"));
        assertTrue(controller.contains("PHONE_VIEW_DENIED"));
        assertTrue(controller.contains("CUSTOMER_ACCESS_DENIED"));
        assertTrue(controller.contains("PHONE_DECRYPT_FAILED"));
        assertTrue(controller.contains("PHONE_PRIVACY_NOT_READY"));
        assertTrue(controller.contains("personal_info_access_log"));
        assertTrue(controller.contains("业务员点击查看完整手机号"));
        assertTrue(currentUserService.contains("hasPermission"));
        assertFalse(currentUserService.contains("if (user.isAdmin()) {\n            return true;"));
    }

    @Test
    void surveysEndpointRoutesPhoneKeywordsToSurveyHashQueries() throws Exception {
        String controller = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "controller", "SurveyController.java"
        ));
        String mapper = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "mapper", "SurveyMapper.java"
        ));

        assertTrue(controller.contains("resolveSurveySearch(keyword)"));
        assertTrue(controller.contains("buildPhoneHash"));
        assertTrue(controller.contains("buildPhoneSuffix4HashFromLast4"));
        assertTrue(controller.contains("normalizeLegacyDigitsPhone"));
        assertTrue(controller.contains("ResponseStatusException(HttpStatus.BAD_REQUEST"));
        assertTrue(mapper.contains("phone_hash = #{phoneHash}"));
        assertTrue(mapper.contains("phone_suffix4_hash = #{phoneSuffix4Hash}"));
        assertFalse(mapper.contains("phone LIKE"));
    }
}
