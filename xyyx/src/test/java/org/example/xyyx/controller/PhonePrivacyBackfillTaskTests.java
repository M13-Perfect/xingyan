package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhonePrivacyBackfillTaskTests {

    @Test
    void backfillTaskUsesHashContractWithoutLoggingPlainPhones() throws Exception {
        Path taskPath = Path.of(
                "src", "main", "java", "org", "example", "xyyx", "task", "PhonePrivacyBackfillTask.java"
        );
        assertTrue(Files.exists(taskPath));
        String task = Files.readString(taskPath);

        assertTrue(task.contains("phone-privacy-backfill"));
        assertTrue(task.contains("--table"));
        assertTrue(task.contains("--tenantId"));
        assertTrue(task.contains("--batchSize"));
        assertTrue(task.contains("--dryRun"));
        assertTrue(task.contains("--legacyDigits"));
        assertTrue(task.contains("--rekey"));
        assertTrue(task.contains("--confirmRekey"));
        assertTrue(task.contains("phone_hash IS NULL OR phone_mask IS NULL"));
        assertTrue(task.contains("phone_mask = ''"));
        assertTrue(task.contains("phone_ciphertext IS NULL"));
        assertTrue(task.contains("phone_iv IS NULL OR phone_tag IS NULL OR phone_suffix4_hash IS NULL"));
        assertTrue(task.contains("WHERE id = ? AND tenant_id = ? AND phone IS NOT NULL"));
        assertTrue(task.contains("--confirmRekey=true required"));
        assertTrue(task.contains("normalizeForBackfill"));
        assertTrue(task.contains("normalizeLegacyDigitsPhone"));
        assertTrue(task.contains("legacyDigitsNormalizedVersion"));
        assertTrue(task.contains("buildPhoneHash(phone.value()"));
        assertTrue(task.contains("buildPhoneSuffix4HashFromLast4"));
        assertTrue(task.contains("encryptPhone(phone.value()"));
        assertTrue(task.contains("maskPhone(normalized"));
        assertTrue(task.contains("applicationContext.close()"));
        assertTrue(task.contains("surveyId="));
        assertFalse(task.contains("row.getPhone() +"));
        assertFalse(task.contains("+ row.getPhone()"));

        String mapper = Files.readString(Path.of(
                "src", "main", "java", "org", "example", "xyyx", "mapper", "SurveyMapper.java"
        ));
        assertTrue(mapper.contains("selectPhoneRekeyBatch"));
        assertTrue(mapper.contains("updatePhonePrivacyRekey"));
        assertTrue(mapper.contains("phone_hash IS NULL OR phone_mask IS NULL"));
        assertTrue(mapper.contains("phone_mask = ''"));
        assertTrue(mapper.contains("phone_ciphertext IS NULL"));
        assertTrue(mapper.contains("phone_iv IS NULL OR phone_tag IS NULL OR phone_suffix4_hash IS NULL"));
        assertTrue(mapper.contains("WHERE id = #{id} AND tenant_id = #{tenantId} AND phone IS NOT NULL"));
    }
}
