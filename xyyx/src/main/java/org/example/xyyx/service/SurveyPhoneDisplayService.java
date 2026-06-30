package org.example.xyyx.service;

import jakarta.servlet.http.HttpServletRequest;
import org.example.xyyx.entity.Survey;
import org.example.xyyx.mapper.SurveyMapper;
import org.example.xyyx.service.CurrentUserService.CurrentUser;
import org.example.xyyx.service.PhoneRevealSessionService.Page;
import org.example.xyyx.service.TenantSystemSettingsService.TenantSystemSettings;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SurveyPhoneDisplayService {
    private static final String MODE_MASKED = "MASKED";

    private final SurveyMapper surveyMapper;
    private final PhonePrivacyService phonePrivacyService;
    private final JdbcTemplate jdbcTemplate;
    private final InMemoryRateLimiter rateLimiter;

    public SurveyPhoneDisplayService(
            SurveyMapper surveyMapper,
            PhonePrivacyService phonePrivacyService,
            JdbcTemplate jdbcTemplate,
            InMemoryRateLimiter rateLimiter) {
        this.surveyMapper = surveyMapper;
        this.phonePrivacyService = phonePrivacyService;
        this.jdbcTemplate = jdbcTemplate;
        this.rateLimiter = rateLimiter;
    }

    public List<Map<String, Object>> toResponseRows(
            List<Survey> currentPage,
            CurrentUser user,
            Long userId,
            TenantSystemSettings settings,
            boolean sessionActivated,
            Map<Long, Long> grantedOrderExpiry,
            String tenantId,
            String queryHash,
            int page,
            int pageSize,
            HttpServletRequest request) {
        List<Map<String, Object>> rows = currentPage.stream().map(this::maskedRow).collect(Collectors.toList());
        applyPolicy(rows, settings, sessionActivated);
        if (currentPage.isEmpty()) {
            return rows;
        }
        GlobalPhoneDisplayPolicyMode policy = settings.phoneDisplayPolicy();
        if (policy == GlobalPhoneDisplayPolicyMode.CLICK_TO_SESSION_VISIBLE && sessionActivated) {
            // ON: whole current page revealed for the login session.
            revealRows(rows, currentPage, user, userId, settings, null, tenantId, queryHash, page, pageSize, request);
        } else if (policy == GlobalPhoneDisplayPolicyMode.SINGLE_ORDER_TIMED_REVEAL
                && grantedOrderExpiry != null && !grantedOrderExpiry.isEmpty()) {
            // OFF: only orders with an active 300s grant are revealed; the rest stay masked + clickable.
            revealRows(rows, currentPage, user, userId, settings, grantedOrderExpiry, tenantId, queryHash, page, pageSize, request);
        }
        return rows;
    }

    /**
     * Decrypt and reveal rows in-place. grantedOrderExpiry == null means session mode (reveal every row);
     * non-null means OFF mode (reveal only ids present in the map, stamping each row's phoneRevealExpiresAt).
     */
    private void revealRows(
            List<Map<String, Object>> rows,
            List<Survey> currentPage,
            CurrentUser user,
            Long userId,
            TenantSystemSettings settings,
            Map<Long, Long> grantedOrderExpiry,
            String tenantId,
            String queryHash,
            int page,
            int pageSize,
            HttpServletRequest request) {
        int count = currentPage.size();
        rateLimiter.require("phone-session-batch:user:" + userId, 500, 60_000, count, "PHONE_REVEAL_SESSION_TOO_FREQUENT");
        rateLimiter.require("phone-session-batch:ip:" + ip(request), 1000, 60_000, count, "PHONE_REVEAL_SESSION_TOO_FREQUENT");

        List<Long> ids = currentPage.stream().map(Survey::getId).toList();
        List<Survey> secrets = user.isAdmin()
                ? surveyMapper.selectAdminPhoneSecretsByIds(tenantId, ids)
                : surveyMapper.selectStaffPhoneSecretsByIds(user.username(), tenantId, ids);
        Map<Long, Survey> byId = secrets.stream().collect(Collectors.toMap(Survey::getId, Function.identity()));

        int revealed = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            if (grantedOrderExpiry != null && !grantedOrderExpiry.containsKey(id)) {
                continue; // OFF: no active grant for this order -> leave masked + canRevealPhone=true.
            }
            Survey secret = byId.get(id);
            if (secret == null || !hasPrivacyFields(secret)) {
                row.put("phoneRevealStatus", "PHONE_PRIVACY_NOT_READY");
                continue;
            }
            try {
                row.put("phoneDisplay", phonePrivacyService.decryptPhone(secret));
                row.put("phoneRevealed", true);
                row.put("phoneDisplayPolicy", settings.phoneDisplayPolicy().name());
                row.put("phoneRevealStatus", "REVEALED");
                row.put("canRevealPhone", false);
                if (grantedOrderExpiry != null) {
                    row.put("phoneRevealExpiresAt", grantedOrderExpiry.get(id));
                }
                revealed++;
            } catch (PhonePrivacyException e) {
                String code = e.code();
                row.put("phoneRevealStatus", code == null || code.isBlank() ? "PHONE_PRIVACY_NOT_READY" : code);
            }
        }
        if (revealed > 0) {
            audit(tenantId, user, userId, settings, "PHONE_REVEAL_SESSION_BATCH", revealed, ids.toString(), queryHash, new Page(page, pageSize), request);
        }
    }

    private void applyPolicy(List<Map<String, Object>> rows, TenantSystemSettings settings, boolean sessionActivated) {
        for (Map<String, Object> row : rows) {
            row.put("phoneDisplayPolicy", settings.phoneDisplayPolicy().name());
            if (settings.phoneDisplayPolicy() == GlobalPhoneDisplayPolicyMode.MASKED_ONLY || sessionActivated) {
                row.put("canRevealPhone", false);
            } else {
                row.put("canRevealPhone", true);
            }
            row.put("phoneRevealStatus", "MASKED");
        }
    }

    private Map<String, Object> maskedRow(Survey survey) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", survey.getId());
        row.put("tenantId", survey.getTenantId());
        row.put("customerUuid", survey.getCustomerUuid());
        row.put("name", survey.getName());
        row.put("phoneMask", survey.getPhoneMask());
        row.put("phoneDisplay", survey.getPhoneMask() == null || survey.getPhoneMask().isBlank() ? "无" : survey.getPhoneMask());
        row.put("phoneRevealed", false);
        row.put("phoneDisplayMode", MODE_MASKED);
        row.put("phoneDisplayPolicy", MODE_MASKED);
        row.put("canRevealPhone", false);
        row.put("phoneRevealStatus", "MASKED");
        row.put("wechat", survey.getWechat());
        row.put("socialAccount", survey.getSocialAccount());
        row.put("city", survey.getCity());
        row.put("project", survey.getProject());
        row.put("budget", survey.getBudget());
        row.put("remarks", survey.getRemarks());
        row.put("owner", survey.getOwner());
        row.put("visibility", survey.getVisibility());
        row.put("status", survey.getStatus());
        row.put("createTime", survey.getCreateTime());
        row.put("nextSurveyDate", survey.getNextSurveyDate());
        row.put("sharedUsers", survey.getSharedUsers());
        return row;
    }

    private boolean hasPrivacyFields(Survey survey) {
        return survey.getPhoneCiphertext() != null
                && survey.getPhoneIv() != null
                && survey.getPhoneTag() != null
                && survey.getPhoneEncKeyVersion() != null
                && survey.getPhoneMask() != null
                && !survey.getPhoneMask().isBlank()
                && survey.getCustomerUuid() != null
                && survey.getTenantId() != null;
    }

    private String ip(HttpServletRequest request) {
        return request == null ? "" : request.getRemoteAddr();
    }

    private void audit(String tenantId, CurrentUser user, Long userId, TenantSystemSettings settings, String action, int recordCount,
                       String targetIdsJson, String queryHash, Page page, HttpServletRequest request) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO personal_info_access_log " +
                            "(tenant_id, operator_user_id, actor_username, action, target_type, target_ids_json, record_count, query_hash, page, page_size, reveal_visibility_mode, success, ip_address, user_agent, request_id) " +
                    "VALUES (?, ?, ?, ?, 'SURVEY', ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)",
                    tenantId,
                    userId,
                    user.username(),
                    action,
                    targetIdsJson,
                    recordCount,
                    queryHash,
                    page == null ? null : page.page(),
                    page == null ? null : page.pageSize(),
                    settings.phoneDisplayPolicy().name(),
                    request == null ? null : request.getRemoteAddr(),
                    request == null ? null : trimUserAgent(request.getHeader("User-Agent")),
                    request == null || request.getAttribute("requestId") == null ? null : request.getAttribute("requestId").toString()
            );
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_LOG_UNAVAILABLE");
        }
    }

    private String trimUserAgent(String userAgent) {
        return userAgent == null || userAgent.length() <= 500 ? userAgent : userAgent.substring(0, 500);
    }
}
