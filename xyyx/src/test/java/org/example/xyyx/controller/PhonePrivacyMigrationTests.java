package org.example.xyyx.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhonePrivacyMigrationTests {

    @Test
    void migrationAndBootstrapSchemaContainPhonePrivacyFieldsAndAuditTable() throws Exception {
        String migration = Files.readString(Path.of("..", "db", "migrations", "V20260628_001_add_phone_privacy.sql"));
        String bootstrap = Files.readString(Path.of("..", "xyyx_db.sql"));
        String preflight = Files.readString(Path.of("..", "db", "verify", "V20260628_preflight_phone_privacy.sql"));
        String verify = Files.readString(Path.of("..", "db", "verify", "V20260628_verify_phone_privacy.sql"));
        String clearLegacyPhone = Files.readString(Path.of("..", "db", "migrations", "V20260628_002_clear_legacy_plain_phone.sql"));
        String revealMigration = Files.readString(Path.of("..", "db", "migrations", "V20260629_001_phone_reveal_session.sql"));
        String revealVerify = Files.readString(Path.of("..", "db", "verify", "V20260629_verify_phone_reveal_session.sql"));
        String settingsMigration = Files.readString(Path.of("..", "db", "migrations", "V20260630_001_tenant_system_settings.sql"));
        String settingsVerify = Files.readString(Path.of("..", "db", "verify", "V20260630_verify_tenant_system_settings.sql"));

        for (String schema : new String[]{migration, bootstrap}) {
            assertTrue(schema.contains("`tenant_id`"));
            assertTrue(schema.contains("`customer_uuid`"));
            assertTrue(schema.contains("`phone_ciphertext`"));
            assertTrue(schema.contains("`phone_iv`"));
            assertTrue(schema.contains("`phone_tag`"));
            assertTrue(schema.contains("`phone_hash`"));
            assertTrue(schema.contains("`phone_mask`"));
            assertTrue(schema.contains("`phone_suffix4_hash`"));
            assertTrue(schema.contains("`personal_info_access_log`"));
            assertTrue(schema.contains("`role_permission`"));
            assertTrue(schema.contains("PHONE_VIEW_FULL"));
            assertTrue(schema.contains("idx_survey_tenant_phone_hash"));
            assertTrue(schema.contains("idx_survey_tenant_phone_suffix4_hash"));
        }

        assertTrue(verify.contains("need_backfill_rows"));
        assertTrue(verify.contains("encrypted_rows"));
        assertTrue(verify.contains("phone_suffix4_hash` IS NULL"));
        assertTrue(verify.contains("phone_mask` = ''"));
        assertTrue(verify.contains("information_schema.COLUMNS"));
        assertFalse(verify.contains("SELECT phone"));
        assertTrue(clearLegacyPhone.contains("phone_suffix4_hash` IS NOT NULL"));
        assertTrue(clearLegacyPhone.contains("phone_mask` <> ''"));
        assertTrue(clearLegacyPhone.contains("phone_enc_key_version` IS NOT NULL"));
        assertTrue(preflight.contains("MISSING"));
        assertTrue(preflight.contains("legacy_phone_rows"));
        assertTrue(preflight.contains("information_schema.COLUMNS"));
        assertFalse(preflight.contains("SELECT phone"));

        assertTrue(revealMigration.contains("`privacy_phone_reveal_policy`"));
        assertTrue(revealMigration.contains("`phone_reveal_login_session`"));
        assertTrue(revealMigration.contains("`personal_info_access_log`"));
        assertTrue(revealMigration.contains("PHONE_VIEW_FULL_SESSION"));
        assertTrue(revealMigration.contains("PRIVACY_POLICY_MANAGE"));
        assertTrue(revealMigration.contains("INSERT IGNORE"));
        assertFalse(revealMigration.contains("DROP TABLE"));
        assertFalse(revealMigration.contains("DROP COLUMN"));

        assertTrue(revealVerify.contains("privacy_phone_reveal_policy"));
        assertTrue(revealVerify.contains("phone_reveal_login_session"));
        assertTrue(revealVerify.contains("PHONE_VIEW_FULL_SESSION"));
        assertTrue(revealVerify.contains("PRIVACY_POLICY_MANAGE"));
        assertTrue(revealVerify.contains("need_backfill_rows"));
        assertFalse(revealVerify.contains("SELECT phone"));

        assertTrue(settingsMigration.contains("`tenant_system_settings`"));
        assertTrue(settingsMigration.contains("CLICK_TO_SESSION_VISIBLE"));
        assertTrue(settingsMigration.contains("MASKED_ONLY"));
        assertTrue(settingsMigration.contains("`order_page_size`"));
        assertFalse(settingsMigration.contains("employee_phone_display_policy"));
        assertFalse(settingsMigration.contains("DROP TABLE"));
        assertFalse(settingsMigration.contains("DROP COLUMN"));

        assertTrue(settingsVerify.contains("tenant_system_settings"));
        assertTrue(settingsVerify.contains("order_page_size"));
        assertTrue(settingsVerify.contains("phone_display_policy"));
        assertTrue(settingsVerify.contains("CLICK_TO_SESSION_VISIBLE"));
        assertFalse(settingsVerify.contains("SELECT phone"));
    }
}
