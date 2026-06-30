package org.example.xyyx.task;

import org.example.xyyx.entity.Survey;
import org.example.xyyx.mapper.SurveyMapper;
import org.example.xyyx.service.PhonePrivacyException;
import org.example.xyyx.service.PhonePrivacyService;
import org.example.xyyx.service.PhonePrivacyService.EncryptedPhone;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PhonePrivacyBackfillTask implements ApplicationRunner {
    private static final String TASK_NAME = "phone-privacy-backfill";
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final String UPDATE_GUARD = "WHERE id = ? AND phone IS NOT NULL AND (phone_hash IS NULL OR phone_mask IS NULL OR phone_mask = '' OR phone_ciphertext IS NULL OR phone_iv IS NULL OR phone_tag IS NULL OR phone_suffix4_hash IS NULL)";
    private static final String REKEY_GUARD = "WHERE id = ? AND tenant_id = ? AND phone IS NOT NULL";
    private static final String DRY_RUN_OPTION = "--dryRun";
    private static final String LEGACY_DIGITS_OPTION = "--legacyDigits";
    private static final String REKEY_OPTION = "--rekey";
    private static final String CONFIRM_REKEY_OPTION = "--confirmRekey";

    private final SurveyMapper surveyMapper;
    private final PhonePrivacyService phonePrivacyService;
    private final ConfigurableApplicationContext applicationContext;

    public PhonePrivacyBackfillTask(
            SurveyMapper surveyMapper,
            PhonePrivacyService phonePrivacyService,
            ConfigurableApplicationContext applicationContext
    ) {
        this.surveyMapper = surveyMapper;
        this.phonePrivacyService = phonePrivacyService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains(TASK_NAME)) {
            return;
        }
        String table = option(args, "table", "");
        if (!"survey".equals(table)) {
            throw new IllegalArgumentException("--table=survey required");
        }
        String tenantId = option(args, "tenantId", "");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("--tenantId required");
        }
        int batchSize = parseBatchSize(option(args, "batchSize", Integer.toString(DEFAULT_BATCH_SIZE)));
        boolean dryRun = Boolean.parseBoolean(option(args, DRY_RUN_OPTION.substring(2), "true"));
        boolean legacyDigits = Boolean.parseBoolean(option(args, LEGACY_DIGITS_OPTION.substring(2), "false"));
        boolean rekey = Boolean.parseBoolean(option(args, REKEY_OPTION.substring(2), "false"));
        boolean confirmRekey = Boolean.parseBoolean(option(args, CONFIRM_REKEY_OPTION.substring(2), "false"));
        if (rekey && !dryRun && !confirmRekey) {
            throw new IllegalArgumentException("--confirmRekey=true required when --rekey=true --dryRun=false");
        }

        try {
            BackfillStats stats = backfill(tenantId, batchSize, dryRun, legacyDigits, rekey);
            System.out.printf(
                    "%s scanned=%d updated=%d invalid=%d skipped=%d legacy=%d dryRun=%s legacyDigits=%s rekey=%s guard=\"%s\"%n",
                    TASK_NAME, stats.scanned(), stats.updated(), stats.invalid(), stats.skipped(), stats.legacy(),
                    dryRun, legacyDigits, rekey, rekey ? REKEY_GUARD : UPDATE_GUARD
            );
        } finally {
            applicationContext.close();
        }
    }

    BackfillStats backfill(String tenantId, int batchSize, boolean dryRun, boolean legacyDigits, boolean rekey) {
        long afterId = 0L;
        int scanned = 0;
        int updated = 0;
        int invalid = 0;
        int skipped = 0;
        int legacy = 0;

        while (true) {
            List<Survey> batch = rekey
                    ? surveyMapper.selectPhoneRekeyBatch(tenantId, afterId, batchSize)
                    : surveyMapper.selectPhoneBackfillBatch(tenantId, afterId, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            for (Survey row : batch) {
                afterId = row.getId();
                scanned++;
                try {
                    BackfilledSurvey backfilled = buildBackfilledSurvey(row, tenantId, legacyDigits);
                    if (backfilled.legacy()) {
                        legacy++;
                    }
                    if (dryRun) {
                        continue;
                    }
                    int changed = rekey
                            ? surveyMapper.updatePhonePrivacyRekey(backfilled.survey())
                            : surveyMapper.updatePhonePrivacyBackfill(backfilled.survey());
                    updated += changed;
                    if (changed == 0) {
                        skipped++;
                    }
                } catch (PhonePrivacyException e) {
                    invalid++;
                    System.out.printf("%s surveyId=%d code=%s%n", TASK_NAME, row.getId(), e.code());
                }
            }
            if (batch.size() < batchSize) {
                break;
            }
        }
        return new BackfillStats(scanned, updated, invalid, skipped, legacy);
    }

    private BackfilledSurvey buildBackfilledSurvey(Survey row, String tenantId, boolean legacyDigits) {
        String customerUuid = row.getCustomerUuid();
        if (customerUuid == null || customerUuid.isBlank()) {
            customerUuid = Long.toString(row.getId());
        }
        PhoneValue phone = normalizeForBackfill(row.getPhone(), legacyDigits);
        EncryptedPhone encrypted = phonePrivacyService.encryptPhone(phone.value(), tenantId, customerUuid);

        Survey survey = new Survey();
        survey.setId(row.getId());
        survey.setTenantId(tenantId);
        survey.setCustomerUuid(customerUuid);
        survey.setPhoneCiphertext(encrypted.ciphertext());
        survey.setPhoneIv(encrypted.iv());
        survey.setPhoneTag(encrypted.tag());
        survey.setPhoneEncKeyVersion(encrypted.keyVersion());
        survey.setPhoneHash(phonePrivacyService.buildPhoneHash(phone.value(), tenantId));
        survey.setPhoneHashKeyVersion(phonePrivacyService.currentHashKeyVersion());
        survey.setPhoneMask(phone.mask());
        survey.setPhoneSuffix4Hash(phone.value().length() >= 4
                ? phonePrivacyService.buildPhoneSuffix4HashFromLast4(phone.value().substring(phone.value().length() - 4), tenantId)
                : null);
        survey.setPhoneRegion(phone.region());
        survey.setPhoneNormalizedVersion(phone.version());
        return new BackfilledSurvey(survey, phone.legacy());
    }

    private PhoneValue normalizeForBackfill(String rawPhone, boolean legacyDigits) {
        try {
            String normalized = phonePrivacyService.normalizePhone(rawPhone, "CN");
            return new PhoneValue(
                    normalized,
                    phonePrivacyService.maskPhone(normalized),
                    "CN",
                    phonePrivacyService.normalizedVersion(),
                    false
            );
        } catch (PhonePrivacyException e) {
            if (!legacyDigits || !"PHONE_INVALID".equals(e.code())) {
                throw e;
            }
            String digits = phonePrivacyService.normalizeLegacyDigitsPhone(rawPhone);
            return new PhoneValue(
                    digits,
                    phonePrivacyService.maskLegacyDigitsPhone(digits),
                    "LEGACY",
                    phonePrivacyService.legacyDigitsNormalizedVersion(),
                    true
            );
        }
    }

    private static int parseBatchSize(String value) {
        int batchSize = Integer.parseInt(value);
        if (batchSize <= 0) {
            throw new IllegalArgumentException("--batchSize must be positive");
        }
        return batchSize;
    }

    private static String option(ApplicationArguments args, String name, String defaultValue) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return defaultValue;
        }
        return values.get(0);
    }

    record PhoneValue(String value, String mask, String region, String version, boolean legacy) {
    }

    record BackfilledSurvey(Survey survey, boolean legacy) {
    }

    record BackfillStats(int scanned, int updated, int invalid, int skipped, int legacy) {
    }
}
