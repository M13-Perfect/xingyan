package org.example.xyyx.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.xyyx.entity.Survey;
import org.example.xyyx.mapper.SurveyMapper;
import org.example.xyyx.service.CasdoorAdminService;
import org.example.xyyx.service.CryptoService;
import org.example.xyyx.service.ApiException;
import org.example.xyyx.service.CurrentUserService;
import org.example.xyyx.service.CurrentUserService.CurrentUser;
import org.example.xyyx.service.PhonePrivacyException;
import org.example.xyyx.service.PhonePrivacyService;
import org.example.xyyx.service.PhonePrivacyService.EncryptedPhone;
import org.example.xyyx.service.PhoneRevealSessionService;
import org.example.xyyx.service.SurveyPhoneDisplayService;
import org.example.xyyx.service.InMemoryRateLimiter;
import org.example.xyyx.service.TenantSystemSettingsService;
import org.example.xyyx.service.TenantSystemSettingsService.TenantSystemSettings;
import org.example.xyyx.service.GlobalPhoneDisplayPolicyMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class SurveyController {
    private static final String DEFAULT_TENANT_ID = "default";
    private static final int PHONE_GCM_IV_BYTES = 12;
    private static final int PHONE_GCM_TAG_BYTES = 16;
    private static final Logger log = LoggerFactory.getLogger(SurveyController.class);

    @Autowired
    private SurveyMapper surveyMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CryptoService cryptoService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CurrentUserService currentUserService;
    @Autowired
    private CasdoorAdminService casdoorAdminService;
    @Autowired
    private PhonePrivacyService phonePrivacyService;
    @Autowired
    private PhoneRevealSessionService phoneRevealSessionService;
    @Autowired
    private SurveyPhoneDisplayService surveyPhoneDisplayService;
    @Autowired
    private TenantSystemSettingsService tenantSystemSettingsService;
    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @GetMapping("/security/public-key")
    public Map<String, Object> getPublicKey() {
        Map<String, Object> result = new HashMap<>();
        result.put("publicKey", cryptoService.getPublicKeyBase64());
        result.put("algorithm", "RSA-OAEP-256");
        return result;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginData) {
        Map<String, Object> result = new HashMap<>();
        String username = loginData.get("username");
        String encryptedPassword = loginData.get("password");
        if (username == null || username.isBlank() || encryptedPassword == null || encryptedPassword.isBlank()) {
            result.put("success", false);
            result.put("message", "账号或密码不能为空");
            return result;
        }

        String plainPassword;
        try {
            plainPassword = cryptoService.decryptPassword(encryptedPassword);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", "密码解密失败");
            return result;
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT password, role FROM user WHERE username = ?",
                username
        );
        if (users.isEmpty()) {
            result.put("success", false);
            result.put("message", "账号或密码错误");
            return result;
        }

        Map<String, Object> dbUser = users.get(0);
        String passwordHash = asString(dbUser.get("password"));
        if (passwordHash == null || !passwordEncoder.matches(plainPassword, passwordHash)) {
            result.put("success", false);
            result.put("message", "账号或密码错误");
            return result;
        }

        result.put("success", true);
        result.put("role", asString(dbUser.get("role")));
        result.put("username", username);
        return result;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> getUsers(@AuthenticationPrincipal Jwt jwt) {
        currentUserService.requireAdmin(jwt);
        return new ArrayList<>(jdbcTemplate.queryForList("SELECT id, username, role FROM user WHERE role != 'admin'"));
    }

    @GetMapping("/app-settings")
    public Map<String, Object> getAppSettings(@AuthenticationPrincipal Jwt jwt) {
        currentUserService.requireUser(jwt);
        TenantSystemSettings settings = tenantSystemSettingsService.getEffectiveSettings(DEFAULT_TENANT_ID);
        return Map.of("orderPageSize", settings.orderPageSize());
    }

    @GetMapping("/admin/system-settings")
    public TenantSystemSettings getSystemSettings(@AuthenticationPrincipal Jwt jwt) {
        requirePolicyManager(jwt);
        return tenantSystemSettingsService.getEffectiveSettings(DEFAULT_TENANT_ID);
    }

    @PutMapping("/admin/system-settings")
    public TenantSystemSettings updateSystemSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        CurrentUser operator = requirePolicyManager(jwt);
        return tenantSystemSettingsService.updateSettings(DEFAULT_TENANT_ID, payload, operator, currentUserService.userId(operator), request);
    }

    @PostMapping("/users")
    @Transactional
    public String addUser(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> payload) {
        currentUserService.requireAdmin(jwt);
        String username = payload.get("username");
        String encryptedPassword = payload.get("password");
        String role = payload.getOrDefault("role", "staff");
        if (username == null || username.isBlank()) return "账号不能为空";
        if (encryptedPassword == null || encryptedPassword.isBlank()) return "初始密码不能为空";
        if ("admin".equalsIgnoreCase(role)) return "禁止创建管理员账号";

        if (!"staff".equalsIgnoreCase(role)) return "仅允许创建业务专员";
        role = "staff";

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user WHERE username = ?", Integer.class, username);
        if (count != null && count > 0) return "账号已存在";

        String plainPassword;
        try {
            plainPassword = cryptoService.decryptPassword(encryptedPassword);
        } catch (IllegalArgumentException e) {
            return "密码解密失败";
        }

        if (plainPassword.length() < 6) return "密码长度至少 6 位";

        String passwordHash = passwordEncoder.encode(plainPassword);
        jdbcTemplate.update("INSERT INTO user (username, password, role) VALUES (?, ?, ?)", username, passwordHash, role);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
        int roleRows = jdbcTemplate.update(
                "INSERT INTO user_role (user_id, role_id, assigned_by_user_id, created_at, revoked_at) " +
                        "SELECT ?, r.id, NULL, CURRENT_TIMESTAMP, NULL FROM sys_role r WHERE r.code = ?",
                userId,
                "STAFF"
        );
        if (roleRows != 1) {
            throw new IllegalStateException("Missing STAFF role seed");
        }
        // 本地 user + user_role 均已插入后再同步 Casdoor：失败会抛异常触发本地事务回滚，保持一致。
        // 已知可接受窗口：Casdoor 创建成功但随后本地提交失败时，可能产生 Casdoor 孤儿用户。
        casdoorAdminService.createUser(username, plainPassword);
        return "员工添加成功";
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public String deleteUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        currentUserService.requireAdmin(jwt);
        List<Map<String, Object>> targetUsers = jdbcTemplate.queryForList("SELECT username, role FROM user WHERE id = ?", id);
        if (targetUsers.isEmpty()) return "员工不存在";
        if ("admin".equals(asString(targetUsers.get(0).get("role")))) return "禁止删除管理员账号";
        String username = asString(targetUsers.get(0).get("username"));

        jdbcTemplate.update("UPDATE user_role SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL", id);
        int affected = jdbcTemplate.update("DELETE FROM user WHERE id = ?", id);
        if (affected > 0) {
            // 尽力而为：本地删除是权威，Casdoor 删除失败已在 service 内吞掉。
            casdoorAdminService.deleteUser(username);
            return "员工删除成功";
        }
        return "员工不存在";
    }

    @PutMapping("/users/{id}/password")
    public String updateUserPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        currentUserService.requireAdmin(jwt);
        String encryptedPassword = payload.get("password");
        if (encryptedPassword == null || encryptedPassword.isBlank()) return "新密码不能为空";

        List<Map<String, Object>> targetUsers = jdbcTemplate.queryForList("SELECT role FROM user WHERE id = ?", id);
        if (targetUsers.isEmpty()) return "员工不存在";
        if ("admin".equals(asString(targetUsers.get(0).get("role")))) return "禁止修改管理员密码";

        String plainPassword;
        try {
            plainPassword = cryptoService.decryptPassword(encryptedPassword);
        } catch (IllegalArgumentException e) {
            return "密码解密失败";
        }
        if (plainPassword.length() < 6) return "密码长度至少 6 位";

        String passwordHash = passwordEncoder.encode(plainPassword);
        jdbcTemplate.update("UPDATE user SET password = ? WHERE id = ?", passwordHash, id);
        return "员工密码修改成功";
    }

    @GetMapping("/surveys/pending-count")
    public int getPendingCount(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser currentUser = currentUserService.requireUser(jwt);
        if (currentUser.isAdmin()) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM survey WHERE tenant_id = ? AND status = '未处理'", Integer.class, DEFAULT_TENANT_ID);
        } else {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM survey WHERE tenant_id = ? AND status = '未处理' AND (owner = ? OR visibility = 'PUBLIC' OR FIND_IN_SET(?, shared_users) > 0)",
                    Integer.class,
                    DEFAULT_TENANT_ID,
                    currentUser.username(),
                    currentUser.username()
            );
        }
    }

    @GetMapping("/surveys/revisit-count")
    public Map<String, Object> getRevisitCount(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser currentUser = currentUserService.requireUser(jwt);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        int today;
        int overdue;
        if (currentUser.isAdmin()) {
            today = surveyMapper.countRevisitTodayAdmin(DEFAULT_TENANT_ID, todayStart, tomorrowStart);
            overdue = surveyMapper.countRevisitOverdueAdmin(DEFAULT_TENANT_ID, todayStart);
        } else {
            today = surveyMapper.countRevisitTodayStaff(currentUser.username(), DEFAULT_TENANT_ID, todayStart, tomorrowStart);
            overdue = surveyMapper.countRevisitOverdueStaff(currentUser.username(), DEFAULT_TENANT_ID, todayStart);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("today", today);
        response.put("overdue", overdue);
        response.put("due", today + overdue);
        return response;
    }

    @GetMapping("/surveys")
    public Map<String, Object> getSurveys(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String revisit,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {

        if (page < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        List<Survey> list;
        int total;
        String queryStatus = ("全部".equals(status) || status == null || status.isEmpty()) ? null : status;
        LocalDateTime dueBefore = "due".equals(revisit) ? LocalDate.now().plusDays(1).atStartOfDay() : null;
        CurrentUser currentUser = currentUserService.requireUser(jwt);
        Long viewerUserId = currentUserService.userId(currentUser);
        TenantSystemSettings settings = tenantSystemSettingsService.getEffectiveSettings(DEFAULT_TENANT_ID);
        int pageSize = tenantSystemSettingsService.resolvePageSize(DEFAULT_TENANT_ID, size);
        boolean sessionActivated = settings.phoneDisplayPolicy() == GlobalPhoneDisplayPolicyMode.CLICK_TO_SESSION_VISIBLE
                && phoneRevealSessionService.isActivated(DEFAULT_TENANT_ID, viewerUserId, jwt);
        int offset = (page - 1) * pageSize;

        SurveySearch search = resolveSurveySearch(keyword);

        if (currentUser.isAdmin()) {
            list = surveyMapper.selectAdminPaged(DEFAULT_TENANT_ID, queryStatus, search.keyword(), search.phoneHash(), search.phoneSuffix4Hash(), city, dueBefore, pageSize, offset);
            total = surveyMapper.countAdmin(DEFAULT_TENANT_ID, queryStatus, search.keyword(), search.phoneHash(), search.phoneSuffix4Hash(), city, dueBefore);
        } else {
            list = surveyMapper.selectStaffPaged(currentUser.username(), DEFAULT_TENANT_ID, queryStatus, search.keyword(), search.phoneHash(), search.phoneSuffix4Hash(), city, dueBefore, pageSize, offset);
            total = surveyMapper.countStaff(currentUser.username(), DEFAULT_TENANT_ID, queryStatus, search.keyword(), search.phoneHash(), search.phoneSuffix4Hash(), city, dueBefore);
        }

        // OFF mode only: which orders on this page currently have an active 300s grant for this user+session.
        Map<Long, Long> grantedOrderExpiry = settings.phoneDisplayPolicy() == GlobalPhoneDisplayPolicyMode.SINGLE_ORDER_TIMED_REVEAL
                ? phoneRevealSessionService.revealedOrderExpiry(DEFAULT_TENANT_ID, viewerUserId, list.stream().map(Survey::getId).toList(), jwt)
                : Map.of();

        Map<String, Object> response = new HashMap<>();
        response.put("data", surveyPhoneDisplayService.toResponseRows(
                list,
                currentUser,
                viewerUserId,
                settings,
                sessionActivated,
                grantedOrderExpiry,
                DEFAULT_TENANT_ID,
                queryHash(queryStatus, keyword, city),
                page,
                pageSize,
                request
        ));
        response.put("total", total);
        response.put("pages", (int) Math.ceil((double) total / pageSize));
        return response;
    }

    @PostMapping("/surveys")
    public Map<String, Object> addSurvey(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> params) {
        Map<String, Object> res = new HashMap<>();
        try {
            CurrentUser currentUser = currentUserService.requireUser(jwt);
            Survey s = new Survey();
            s.setTenantId(DEFAULT_TENANT_ID);
            s.setCustomerUuid(UUID.randomUUID().toString());
            s.setName((String) params.get("name"));
            applyPhonePrivacy(s, params.get("phone"));
            s.setWechat(params.get("wechat") != null && !params.get("wechat").toString().isEmpty() ? params.get("wechat").toString() : null);
            s.setSocialAccount(params.get("socialAccount") != null && !params.get("socialAccount").toString().isEmpty() ? params.get("socialAccount").toString() : null);
            s.setCity((String) params.get("city"));
            s.setProject((String) params.get("project"));
            s.setBudget((String) params.get("budget"));
            s.setRemarks((String) params.get("remarks"));
            s.setOwner(currentUser.username());
            int days = Integer.parseInt(params.getOrDefault("remindDays", 3).toString());
            s.setNextSurveyDate(LocalDateTime.now().plusDays(days));
            surveyMapper.insert(s);
            res.put("success", true);
        } catch (PhonePrivacyException e) {
            res.put("success", false);
            res.put("code", e.code());
            res.put("message", e.code());
        } catch (DuplicateKeyException e) {
            res.put("success", false);
            res.put("message", "录入失败：电话或微信号在系统中已存在");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "录入异常");
        }
        return res;
    }

    @PostMapping("/customers/search/phone")
    public Map<String, Object> searchCustomerByPhone(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.requireUser(jwt);
        String normalized = phonePrivacyService.normalizePhone(payload.get("rawPhone"), "CN");
        byte[] phoneHash = phonePrivacyService.buildPhoneHash(normalized, DEFAULT_TENANT_ID);
        List<Survey> list = currentUser.isAdmin()
                ? surveyMapper.selectAdminByPhoneHash(DEFAULT_TENANT_ID, phoneHash)
                : surveyMapper.selectStaffByPhoneHash(currentUser.username(), DEFAULT_TENANT_ID, phoneHash);
        logPersonalInfoAccess(null, currentUser, "PHONE_SEARCH", "PHONE_MASKED", null, true, null, request);
        return surveyListResponse(list, list.size(), 1);
    }

    @PostMapping("/customers/search/phone-suffix4")
    public Map<String, Object> searchCustomerByPhoneSuffix4(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.requireUser(jwt);
        byte[] phoneSuffix4Hash = phonePrivacyService.buildPhoneSuffix4HashFromLast4(payload.get("last4"), DEFAULT_TENANT_ID);
        List<Survey> list = currentUser.isAdmin()
                ? surveyMapper.selectAdminByPhoneSuffix4Hash(DEFAULT_TENANT_ID, phoneSuffix4Hash, 50)
                : surveyMapper.selectStaffByPhoneSuffix4Hash(currentUser.username(), DEFAULT_TENANT_ID, phoneSuffix4Hash, 50);
        logPersonalInfoAccess(null, currentUser, "PHONE_SUFFIX4_SEARCH", "PHONE_MASKED", null, true, null, request);
        return surveyListResponse(list, list.size(), 1);
    }

    @PostMapping({"/surveys/{surveyId}/phone/reveal", "/customers/{surveyId}/phone/reveal"})
    public Map<String, Object> revealCustomerPhone(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long surveyId,
            @RequestBody(required = false) Map<String, String> payload,
            HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.requireUser(jwt);
        Long userId = currentUserService.userId(currentUser);
        rateLimiter.require("phone-reveal:user-survey:" + userId + ":" + surveyId, 1, 5_000, "PHONE_REVEAL_TOO_FREQUENT");
        rateLimiter.require("phone-reveal:user:" + userId, 20, 60_000, "PHONE_REVEAL_TOO_FREQUENT");
        rateLimiter.require("phone-reveal:ip:" + (request == null ? "" : request.getRemoteAddr()), 60, 60_000, "PHONE_REVEAL_TOO_FREQUENT");
        String reason = payload == null ? null : payload.get("reason");
        if (reason == null || reason.isBlank()) {
            reason = "业务员点击查看完整手机号";
        }

        Survey customer = currentUser.isAdmin()
                ? surveyMapper.selectAdminPhoneSecretsById(surveyId, DEFAULT_TENANT_ID)
                : surveyMapper.selectStaffPhoneSecretsById(surveyId, currentUser.username(), DEFAULT_TENANT_ID);
        if (customer == null) {
            logPersonalInfoAccess(null, currentUser, "PHONE_REVEAL", "PHONE_VIEW_FULL", trimReason(reason), false, "CUSTOMER_ACCESS_DENIED", request);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CUSTOMER_ACCESS_DENIED");
        }
        TenantSystemSettings settings = tenantSystemSettingsService.getEffectiveSettings(DEFAULT_TENANT_ID);
        if (settings.phoneDisplayPolicy() == GlobalPhoneDisplayPolicyMode.MASKED_ONLY) {
            logPersonalInfoAccess(customer, currentUser, "PHONE_REVEAL", settings.phoneDisplayPolicy().name(), trimReason(reason), false, "PHONE_VIEW_DENIED", request);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PHONE_VIEW_DENIED");
        }

        try {
            if (!isPhonePrivacyReady(customer)) {
                logPersonalInfoAccess(customer, currentUser, "PHONE_REVEAL", "PHONE_VIEW_FULL", trimReason(reason), false, "PHONE_PRIVACY_NOT_READY", request);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "PHONE_PRIVACY_NOT_READY");
            }
            String phone = phonePrivacyService.decryptPhone(customer);
            Map<String, Object> response = new HashMap<>();
            response.put("phoneDisplay", phone);
            response.put("phoneMask", customer.getPhoneMask());
            response.put("phoneRevealed", true);
            response.put("phoneDisplayPolicy", settings.phoneDisplayPolicy().name());
            if (settings.phoneDisplayPolicy() == GlobalPhoneDisplayPolicyMode.SINGLE_ORDER_TIMED_REVEAL) {
                // OFF: reveal only this order for 300s, server-enforced. Do NOT activate the session-wide grant.
                phoneRevealSessionService.activateOrder(DEFAULT_TENANT_ID, currentUser, userId, surveyId, jwt, request);
                logPersonalInfoAccess(customer, currentUser, "PHONE_REVEAL_SESSION_DETAIL", settings.phoneDisplayPolicy().name(), trimReason(reason), true, null, request);
                long expiresInSeconds = PhoneRevealSessionService.ORDER_REVEAL_SECONDS;
                response.put("phoneRevealStatus", "SINGLE_ORDER_REVEALED");
                response.put("sessionActivated", false);
                response.put("expiresInSeconds", expiresInSeconds);
                response.put("phoneRevealExpiresAt", System.currentTimeMillis() + expiresInSeconds * 1000L);
            } else {
                // ON: activate session-wide reveal for this login session.
                phoneRevealSessionService.activate(DEFAULT_TENANT_ID, currentUser, userId, jwt, request);
                logPersonalInfoAccess(customer, currentUser, "PHONE_REVEAL_SESSION_DETAIL", settings.phoneDisplayPolicy().name(), trimReason(reason), true, null, request);
                response.put("phoneRevealStatus", "SESSION_ACTIVATED");
                response.put("sessionActivated", true);
            }
            return response;
        } catch (PhonePrivacyException e) {
            log.warn("phone reveal decrypt failed requestId={} surveyId={} code={} detail={}",
                    requestId(request), surveyId, e.code(), e.detailCode());
            logPersonalInfoAccess(customer, currentUser, "PHONE_REVEAL", "PHONE_VIEW_FULL", trimReason(reason), false, "PHONE_DECRYPT_FAILED", request);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PHONE_DECRYPT_FAILED");
        }
    }

    @PutMapping("/surveys/{id}/process")
    public String process(@PathVariable Long id) {
        surveyMapper.updateStatus(id);
        return "处理完成";
    }

    @DeleteMapping("/surveys/{id}")
    public String delete(@PathVariable Long id) {
        surveyMapper.deleteById(id);
        return "删除成功";
    }

    @PutMapping("/surveys/{id}/date")
    public Map<String, Object> updateDate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody(required = false) Map<String, String> payload) {
        CurrentUser currentUser = currentUserService.requireUser(jwt);
        String dateValue = payload == null ? null : payload.get("date");
        if (dateValue == null || dateValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DATE_REQUIRED");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateValue);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DATE_INVALID");
        }
        if (date.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DATE_IN_PAST");
        }

        LocalDateTime nextSurveyDate = date.atStartOfDay();
        int updated = currentUser.isAdmin()
                ? surveyMapper.updateAdminNextDate(DEFAULT_TENANT_ID, id, nextSurveyDate)
                : surveyMapper.updateStaffNextDate(currentUser.username(), DEFAULT_TENANT_ID, id, nextSurveyDate);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SURVEY_ACCESS_DENIED");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "提醒日期已保存");
        response.put("nextSurveyDate", date.toString());
        response.put("due", !date.isAfter(LocalDate.now()));
        return response;
    }

    @PutMapping("/surveys/{id}/share")
    public String shareSurvey(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        surveyMapper.updateVisibility(id, payload.get("visibility"), payload.get("sharedUsers"));
        return "共享权限设置成功";
    }

    @PutMapping("/surveys/{id}/remarks")
    public String updateRemarks(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        surveyMapper.updateRemarks(id, payload.get("remarks"));
        return "备注保存成功";
    }

    private void applyPhonePrivacy(Survey survey, Object rawPhoneValue) {
        if (rawPhoneValue == null || rawPhoneValue.toString().isBlank()) {
            survey.setPhone(null);
            return;
        }
        String normalized = phonePrivacyService.normalizePhone(rawPhoneValue.toString(), "CN");
        byte[] phoneHash = phonePrivacyService.buildPhoneHash(normalized, survey.getTenantId());
        if (surveyMapper.countByPhoneHash(survey.getTenantId(), phoneHash) > 0) {
            throw new PhonePrivacyException("PHONE_ALREADY_EXISTS");
        }
        EncryptedPhone encrypted = phonePrivacyService.encryptPhone(normalized, survey.getTenantId(), survey.getCustomerUuid());
        survey.setPhone(null);
        survey.setPhoneCiphertext(encrypted.ciphertext());
        survey.setPhoneIv(encrypted.iv());
        survey.setPhoneTag(encrypted.tag());
        survey.setPhoneEncKeyVersion(encrypted.keyVersion());
        survey.setPhoneHash(phoneHash);
        survey.setPhoneHashKeyVersion(phonePrivacyService.currentHashKeyVersion());
        survey.setPhoneMask(phonePrivacyService.maskPhone(normalized));
        survey.setPhoneSuffix4Hash(phonePrivacyService.buildPhoneSuffix4Hash(normalized, survey.getTenantId()));
        survey.setPhoneRegion("CN");
        survey.setPhoneNormalizedVersion(phonePrivacyService.normalizedVersion());
    }

    private SurveySearch resolveSurveySearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new SurveySearch(null, null, null);
        }
        String value = keyword.trim();
        if (value.matches("\\d{4}")) {
            return new SurveySearch(null, null, phonePrivacyService.buildPhoneSuffix4HashFromLast4(value, DEFAULT_TENANT_ID));
        }
        int digitCount = (int) value.chars().filter(Character::isDigit).count();
        if (digitCount >= 5 && value.matches("[+\\d\\s().-]+")) {
            try {
                String normalized = phonePrivacyService.normalizePhone(value, "CN");
                return new SurveySearch(null, phonePrivacyService.buildPhoneHash(normalized, DEFAULT_TENANT_ID), null);
            } catch (PhonePrivacyException e) {
                if (!"PHONE_INVALID".equals(e.code())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.code(), e);
                }
                String legacyDigits = phonePrivacyService.normalizeLegacyDigitsPhone(value);
                return new SurveySearch(null, phonePrivacyService.buildPhoneHash(legacyDigits, DEFAULT_TENANT_ID), null);
            }
        }
        return new SurveySearch(value, null, null);
    }

    private record SurveySearch(String keyword, byte[] phoneHash, byte[] phoneSuffix4Hash) {
    }

    private Map<String, Object> surveyListResponse(List<Survey> list, int total, int pages) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", list);
        response.put("total", total);
        response.put("pages", pages);
        return response;
    }

    private void logPersonalInfoAccess(
            Survey customer,
            CurrentUser currentUser,
            String action,
            String permission,
            String reason,
            boolean success,
            String errorCode,
            HttpServletRequest request) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO personal_info_access_log " +
                            "(tenant_id, customer_id, customer_uuid, actor_username, action, permission, reason, success, error_code, ip_address, user_agent) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    customer == null || customer.getTenantId() == null ? DEFAULT_TENANT_ID : customer.getTenantId(),
                    customer == null ? null : customer.getId(),
                    customer == null ? null : customer.getCustomerUuid(),
                    currentUser.username(),
                    action,
                    permission,
                    reason,
                    success,
                    errorCode,
                    request == null ? null : request.getRemoteAddr(),
                    request == null ? null : trimUserAgent(request.getHeader("User-Agent"))
            );
        } catch (RuntimeException e) {
            log.error("personal info audit unavailable requestId={} action={} success={} errorCode={}",
                    requestId(request), action, success, errorCode);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_LOG_UNAVAILABLE");
        }
    }

    private boolean isPhonePrivacyReady(Survey customer) {
        return customer.getPhoneCiphertext() != null
                && customer.getPhoneCiphertext().length > 0
                && customer.getPhoneIv() != null
                && customer.getPhoneIv().length == PHONE_GCM_IV_BYTES
                && customer.getPhoneTag() != null
                && customer.getPhoneTag().length == PHONE_GCM_TAG_BYTES
                && customer.getPhoneEncKeyVersion() != null
                && !customer.getPhoneEncKeyVersion().isBlank()
                && customer.getTenantId() != null
                && !customer.getTenantId().isBlank()
                && customer.getCustomerUuid() != null
                && !customer.getCustomerUuid().isBlank();
    }

    private String requestId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    private String trimReason(String reason) {
        String value = reason == null ? null : reason.trim();
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String trimUserAgent(String userAgent) {
        return userAgent == null || userAgent.length() <= 500 ? userAgent : userAgent.substring(0, 500);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private CurrentUser requirePolicyManager(Jwt jwt) {
        CurrentUser operator = currentUserService.requireUser(jwt);
        if (!operator.isAdmin() && !currentUserService.hasPermission(operator, CurrentUserService.PRIVACY_POLICY_MANAGE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PHONE_VIEW_DENIED");
        }
        return operator;
    }

    private String queryHash(String status, String keyword, String city) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((String.valueOf(status) + "|" + String.valueOf(keyword) + "|" + String.valueOf(city)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return "";
        }
    }
}
