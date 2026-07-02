package org.example.xyyx.task;

import org.example.xyyx.service.CasdoorAdminService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 一次性迁移工具：把老账号（本地 user.password 仍是明文）同步到 Casdoor，并把本地密码转成 BCrypt。
 * 只处理 --usernames 显式列出的账号，不做启发式扫描；只打印状态，绝不打印密码明文。
 * 用法：mvn spring-boot:run -Dspring-boot.run.arguments="staff-casdoor-backfill --usernames=alice,bob --dryRun=true"
 */
@Component
public class StaffCasdoorBackfillTask implements ApplicationRunner {
    private static final String TASK_NAME = "staff-casdoor-backfill";
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    private final JdbcTemplate jdbcTemplate;
    private final CasdoorAdminService casdoorAdminService;
    private final PasswordEncoder passwordEncoder;
    private final ConfigurableApplicationContext applicationContext;

    public StaffCasdoorBackfillTask(
            JdbcTemplate jdbcTemplate,
            CasdoorAdminService casdoorAdminService,
            PasswordEncoder passwordEncoder,
            ConfigurableApplicationContext applicationContext
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.casdoorAdminService = casdoorAdminService;
        this.passwordEncoder = passwordEncoder;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.getNonOptionArgs().contains(TASK_NAME)) {
            return;
        }
        String usernamesOption = option(args, "usernames", "");
        if (usernamesOption.isBlank()) {
            throw new IllegalArgumentException("--usernames=user1,user2 required");
        }
        boolean dryRun = Boolean.parseBoolean(option(args, "dryRun", "true"));
        List<String> usernames = List.of(usernamesOption.split(","));

        try {
            int casdoorOk = 0;
            int localUpdated = 0;
            int skipped = 0;
            int failed = 0;
            for (String rawUsername : usernames) {
                String username = rawUsername.trim();
                if (username.isEmpty()) {
                    continue;
                }
                String status = processOne(username, dryRun);
                System.out.printf("%s username=%s status=%s dryRun=%s%n", TASK_NAME, username, status, dryRun);
                switch (status) {
                    case "casdoor_ok_local_updated", "dry_run_would_process" -> {
                        casdoorOk++;
                        localUpdated++;
                    }
                    case "not_found", "already_hashed", "admin_skipped" -> skipped++;
                    default -> failed++;
                }
            }
            System.out.printf("%s done total=%d casdoorOk=%d localUpdated=%d skipped=%d failed=%d%n",
                    TASK_NAME, usernames.size(), casdoorOk, localUpdated, skipped, failed);
        } finally {
            applicationContext.close();
        }
    }

    private String processOne(String username, boolean dryRun) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT password, role FROM user WHERE username = ?", username);
        if (rows.isEmpty()) {
            return "not_found";
        }
        String password = (String) rows.get(0).get("password");
        String role = (String) rows.get(0).get("role");
        if ("admin".equals(role)) {
            return "admin_skipped";
        }
        if (password == null || password.isBlank()) {
            return "not_found";
        }
        if (BCRYPT_PATTERN.matcher(password).matches()) {
            return "already_hashed";
        }
        if (dryRun) {
            return "dry_run_would_process";
        }

        try {
            casdoorAdminService.createUser(username, password, username);
        } catch (Exception e) {
            System.out.printf("%s username=%s casdoor_error=%s%n", TASK_NAME, username, e.getMessage());
            return "casdoor_failed";
        }

        String hash = passwordEncoder.encode(password);
        jdbcTemplate.update("UPDATE user SET password = ? WHERE username = ?", hash, username);
        return "casdoor_ok_local_updated";
    }

    private static String option(ApplicationArguments args, String name, String defaultValue) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return defaultValue;
        }
        return values.get(0);
    }
}
