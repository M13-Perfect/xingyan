package org.example.xyyx.service;

import jakarta.servlet.http.HttpServletRequest;
import org.example.xyyx.service.CurrentUserService.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PhoneRevealSessionService {
    private static final long DEFAULT_SESSION_SECONDS = 8 * 60 * 60L;
    /** OFF (SINGLE_ORDER_TIMED_REVEAL): one click reveals one order for 300s, server-enforced. */
    public static final long ORDER_REVEAL_SECONDS = 300L;

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserService currentUserService;

    public PhoneRevealSessionService(JdbcTemplate jdbcTemplate, CurrentUserService currentUserService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserService = currentUserService;
    }

    public boolean isActivated(String tenantId, Long userId, Jwt jwt) {
        String hash = loginSessionIdHash(tenantId, userId, jwt);
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM phone_reveal_login_session " +
                            "WHERE tenant_id = ? AND user_id = ? AND login_session_id_hash = ? AND enabled = 1 AND expires_at > CURRENT_TIMESTAMP",
                    Integer.class,
                    tenantId,
                    userId,
                    hash
            );
            return count != null && count > 0;
        } catch (BadSqlGrammarException e) {
            return false;
        }
    }

    @Transactional
    public void activate(String tenantId, CurrentUser user, Long userId, Jwt jwt, HttpServletRequest request) {
        String hash = loginSessionIdHash(tenantId, userId, jwt);
        Instant expires = min(jwt == null ? null : jwt.getExpiresAt(), Instant.now().plusSeconds(DEFAULT_SESSION_SECONDS));
        LocalDateTime expiresAt = LocalDateTime.ofInstant(expires, ZoneId.systemDefault());
        try {
            jdbcTemplate.update(
                    "INSERT INTO phone_reveal_login_session " +
                            "(tenant_id, user_id, login_session_id_hash, enabled, enabled_at, expires_at, disabled_at, disabled_reason, policy_version, ip_address, user_agent, created_at, updated_at) " +
                            "VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP, ?, NULL, NULL, NULL, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                            "ON DUPLICATE KEY UPDATE enabled = 1, enabled_at = CURRENT_TIMESTAMP, expires_at = VALUES(expires_at), disabled_at = NULL, disabled_reason = NULL, " +
                            "ip_address = VALUES(ip_address), user_agent = VALUES(user_agent), updated_at = CURRENT_TIMESTAMP",
                    tenantId,
                    userId,
                    hash,
                    expiresAt,
                    ip(request),
                    userAgent(request)
            );
            audit(tenantId, user, userId, hash, "PHONE_SESSION_VISIBILITY_ACTIVATED", true, null, 0, null, null, null, request);
        } catch (BadSqlGrammarException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PHONE_SESSION_UNAVAILABLE");
        }
    }

    /**
     * OFF mode: grant a 300s full-phone reveal for ONE survey, scoped to this user + login session.
     * Throws PHONE_VIEW_SESSION_DENIED if the JWT carries no session id, PHONE_SESSION_UNAVAILABLE if
     * the migration table is missing — same failure surface as the session-wide activate().
     */
    @Transactional
    public void activateOrder(String tenantId, CurrentUser user, Long userId, Long surveyId, Jwt jwt, HttpServletRequest request) {
        String hash = loginSessionIdHash(tenantId, userId, jwt);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(Instant.now().plusSeconds(ORDER_REVEAL_SECONDS), ZoneId.systemDefault());
        try {
            jdbcTemplate.update(
                    "INSERT INTO phone_reveal_order_grant " +
                            "(tenant_id, user_id, login_session_id_hash, survey_id, enabled, expires_at, disabled_at, disabled_reason, ip_address, user_agent, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 1, ?, NULL, NULL, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                            "ON DUPLICATE KEY UPDATE enabled = 1, expires_at = VALUES(expires_at), disabled_at = NULL, disabled_reason = NULL, " +
                            "ip_address = VALUES(ip_address), user_agent = VALUES(user_agent), updated_at = CURRENT_TIMESTAMP",
                    tenantId,
                    userId,
                    hash,
                    surveyId,
                    expiresAt,
                    ip(request),
                    userAgent(request)
            );
            audit(tenantId, user, userId, hash, "PHONE_SESSION_VISIBILITY_ACTIVATED", true, null, 1, "[" + surveyId + "]", null, null, request);
        } catch (BadSqlGrammarException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PHONE_SESSION_UNAVAILABLE");
        }
    }

    /**
     * OFF mode list/detail support: for the given page survey ids, return surveyId -> expiry(epochMillis)
     * for orders that currently have an active grant for this user + login session. Missing session id or
     * missing table degrades to an empty map (all masked) instead of failing the list.
     */
    public Map<Long, Long> revealedOrderExpiry(String tenantId, Long userId, List<Long> surveyIds, Jwt jwt) {
        if (surveyIds == null || surveyIds.isEmpty()) {
            return Map.of();
        }
        Optional<String> loginSessionId = currentUserService.loginSessionId(jwt);
        if (loginSessionId.isEmpty()) {
            return Map.of();
        }
        String hash = sha256(tenantId + "|" + userId + "|" + loginSessionId.get());
        String placeholders = surveyIds.stream().map(x -> "?").collect(Collectors.joining(","));
        Object[] args = new Object[surveyIds.size() + 3];
        args[0] = tenantId;
        args[1] = userId;
        args[2] = hash;
        for (int i = 0; i < surveyIds.size(); i++) {
            args[i + 3] = surveyIds.get(i);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT survey_id, expires_at FROM phone_reveal_order_grant " +
                            "WHERE tenant_id = ? AND user_id = ? AND login_session_id_hash = ? AND survey_id IN (" + placeholders + ") " +
                            "AND enabled = 1 AND expires_at > CURRENT_TIMESTAMP",
                    args
            );
            Map<Long, Long> result = new HashMap<>();
            for (Map<String, Object> row : rows) {
                result.put(((Number) row.get("survey_id")).longValue(), toEpochMillis(row.get("expires_at")));
            }
            return result;
        } catch (BadSqlGrammarException e) {
            return Map.of();
        }
    }

    private Long toEpochMillis(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.getTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return null;
    }

    public void invalidateForUser(String tenantId, Long userId) {
        try {
            jdbcTemplate.update(
                    "UPDATE phone_reveal_login_session SET enabled = 0, disabled_at = CURRENT_TIMESTAMP, disabled_reason = 'USER_INVALIDATED', updated_at = CURRENT_TIMESTAMP " +
                            "WHERE tenant_id = ? AND user_id = ? AND enabled = 1",
                    tenantId,
                    userId
            );
        } catch (BadSqlGrammarException ignored) {
            // ponytail: migration may not exist during rollout; no active DB session can exist then.
        }
        try {
            jdbcTemplate.update(
                    "UPDATE phone_reveal_order_grant SET enabled = 0, disabled_at = CURRENT_TIMESTAMP, disabled_reason = 'USER_INVALIDATED', updated_at = CURRENT_TIMESTAMP " +
                            "WHERE tenant_id = ? AND user_id = ? AND enabled = 1",
                    tenantId,
                    userId
            );
        } catch (BadSqlGrammarException ignored) {
            // ponytail: order-grant table may not exist during rollout.
        }
    }

    public void invalidateForTenant(String tenantId) {
        try {
            jdbcTemplate.update(
                    "UPDATE phone_reveal_login_session SET enabled = 0, disabled_at = CURRENT_TIMESTAMP, disabled_reason = 'SETTINGS_CHANGED', updated_at = CURRENT_TIMESTAMP " +
                            "WHERE tenant_id = ? AND enabled = 1",
                    tenantId
            );
        } catch (BadSqlGrammarException ignored) {
            // ponytail: migration may not exist during rollout; no active DB session can exist then.
        }
        try {
            jdbcTemplate.update(
                    "UPDATE phone_reveal_order_grant SET enabled = 0, disabled_at = CURRENT_TIMESTAMP, disabled_reason = 'SETTINGS_CHANGED', updated_at = CURRENT_TIMESTAMP " +
                            "WHERE tenant_id = ? AND enabled = 1",
                    tenantId
            );
        } catch (BadSqlGrammarException ignored) {
            // ponytail: order-grant table may not exist during rollout.
        }
    }

    public void audit(String tenantId, CurrentUser user, Long userId, String loginSessionIdHash, String action, boolean success,
                      String failureReason, int recordCount, String targetIdsJson, String queryHash, Page page, HttpServletRequest request) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO personal_info_access_log " +
                            "(tenant_id, operator_user_id, actor_username, login_session_id_hash, action, target_type, target_ids_json, record_count, query_hash, page, page_size, reveal_visibility_mode, success, error_code, ip_address, user_agent, request_id) " +
                            "VALUES (?, ?, ?, ?, ?, 'SURVEY', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    tenantId,
                    userId,
                    user.username(),
                    loginSessionIdHash,
                    action,
                    targetIdsJson,
                    recordCount,
                    queryHash,
                    page == null ? null : page.page(),
                    page == null ? null : page.pageSize(),
                    GlobalPhoneDisplayPolicyMode.CLICK_TO_SESSION_VISIBLE.name(),
                    success,
                    failureReason,
                    ip(request),
                    userAgent(request),
                    request == null || request.getAttribute("requestId") == null ? null : request.getAttribute("requestId").toString()
            );
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_LOG_UNAVAILABLE");
        }
    }

    private String loginSessionIdHash(String tenantId, Long userId, Jwt jwt) {
        String loginSessionId = currentUserService.loginSessionId(jwt)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "PHONE_VIEW_SESSION_DENIED"));
        return sha256(tenantId + "|" + userId + "|" + loginSessionId);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
        }
    }

    private Instant min(Instant tokenExpiresAt, Instant fallbackExpiresAt) {
        if (tokenExpiresAt == null) {
            return fallbackExpiresAt;
        }
        return tokenExpiresAt.isBefore(fallbackExpiresAt) ? tokenExpiresAt : fallbackExpiresAt;
    }

    private String ip(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String value = request == null ? null : request.getHeader("User-Agent");
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }

    public record Page(int page, int pageSize) {
    }
}
