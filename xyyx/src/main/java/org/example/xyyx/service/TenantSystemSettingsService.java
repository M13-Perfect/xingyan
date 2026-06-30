package org.example.xyyx.service;

import jakarta.servlet.http.HttpServletRequest;
import org.example.xyyx.service.CurrentUserService.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class TenantSystemSettingsService {
    public static final int DEFAULT_ORDER_PAGE_SIZE = 20;
    private static final int MIN_ORDER_PAGE_SIZE = 1;
    private static final int MAX_ORDER_PAGE_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final PhoneRevealSessionService phoneRevealSessionService;
    private final CurrentUserService currentUserService;

    public TenantSystemSettingsService(
            JdbcTemplate jdbcTemplate,
            PhoneRevealSessionService phoneRevealSessionService,
            CurrentUserService currentUserService) {
        this.jdbcTemplate = jdbcTemplate;
        this.phoneRevealSessionService = phoneRevealSessionService;
        this.currentUserService = currentUserService;
    }

    public TenantSystemSettings getEffectiveSettings(String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT tenant_id, phone_display_policy, order_page_size, updated_at " +
                            "FROM tenant_system_settings WHERE tenant_id = ? AND enabled = 1",
                    tenantId
            );
        } catch (BadSqlGrammarException e) {
            // ponytail: rolling deploy fallback; run V20260630_001 before relying on saved settings.
            return defaults(tenantId);
        }
        if (rows.isEmpty()) {
            return defaults(tenantId);
        }
        Map<String, Object> row = rows.get(0);
        return new TenantSystemSettings(
                string(row.get("tenant_id"), tenantId),
                mode(row.get("phone_display_policy")),
                clamp(integer(row.get("order_page_size"), DEFAULT_ORDER_PAGE_SIZE)),
                row.get("updated_at")
        );
    }

    public int resolvePageSize(String tenantId, Integer requestedSize) {
        return clamp(requestedSize == null ? getEffectiveSettings(tenantId).orderPageSize() : requestedSize);
    }

    @Transactional
    public TenantSystemSettings updateSettings(String tenantId, Map<String, Object> payload, CurrentUser operator,
                                               Long operatorUserId, HttpServletRequest request) {
        requireSettingsManager(operator);
        TenantSystemSettings oldSettings = getEffectiveSettings(tenantId);
        TenantSystemSettingsInput input = TenantSystemSettingsInput.from(payload);
        jdbcTemplate.update(
                "INSERT INTO tenant_system_settings " +
                        "(tenant_id, phone_display_policy, order_page_size, enabled, created_by, updated_by, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 1, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                        "ON DUPLICATE KEY UPDATE phone_display_policy = VALUES(phone_display_policy), order_page_size = VALUES(order_page_size), " +
                        "enabled = 1, updated_by = VALUES(updated_by), updated_at = CURRENT_TIMESTAMP",
                tenantId,
                input.phoneDisplayPolicy().name(),
                input.orderPageSize(),
                operatorUserId,
                operatorUserId
        );
        phoneRevealSessionService.invalidateForTenant(tenantId);
        audit(tenantId, operator, operatorUserId, oldSettings, input, request);
        return getEffectiveSettings(tenantId);
    }

    public void invalidateSettingsCache(String tenantId) {
        // ponytail: no settings cache exists; add eviction here if a real cache is introduced.
    }

    private void requireSettingsManager(CurrentUser operator) {
        if (operator == null || (!operator.isAdmin() && !currentUserService.hasPermission(operator, CurrentUserService.PRIVACY_POLICY_MANAGE))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PHONE_VIEW_DENIED");
        }
    }

    private void audit(String tenantId, CurrentUser operator, Long operatorUserId, TenantSystemSettings oldSettings,
                       TenantSystemSettingsInput input, HttpServletRequest request) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO personal_info_access_log " +
                            "(tenant_id, operator_user_id, actor_username, action, permission, target_type, success, reason, ip_address, user_agent, request_id) " +
                            "VALUES (?, ?, ?, 'UPDATE_SYSTEM_SETTINGS', ?, 'SYSTEM_SETTINGS', 1, ?, ?, ?, ?)",
                    tenantId,
                    operatorUserId,
                    operator.username(),
                    CurrentUserService.PRIVACY_POLICY_MANAGE,
                    "oldPolicy=" + oldSettings.phoneDisplayPolicy() + ";newPolicy=" + input.phoneDisplayPolicy()
                            + ";oldOrderPageSize=" + oldSettings.orderPageSize() + ";newOrderPageSize=" + input.orderPageSize(),
                    request == null ? null : request.getRemoteAddr(),
                    request == null ? null : trim(request.getHeader("User-Agent"), 500),
                    request == null || request.getAttribute("requestId") == null ? null : request.getAttribute("requestId").toString()
            );
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_LOG_UNAVAILABLE");
        }
    }

    private static TenantSystemSettings defaults(String tenantId) {
        return new TenantSystemSettings(tenantId, GlobalPhoneDisplayPolicyMode.CLICK_TO_SESSION_VISIBLE, DEFAULT_ORDER_PAGE_SIZE, null);
    }

    private static GlobalPhoneDisplayPolicyMode mode(Object value) {
        if (value == null || value.toString().isBlank()) {
            return GlobalPhoneDisplayPolicyMode.CLICK_TO_SESSION_VISIBLE;
        }
        try {
            return GlobalPhoneDisplayPolicyMode.valueOf(value.toString());
        } catch (IllegalArgumentException e) {
            return GlobalPhoneDisplayPolicyMode.CLICK_TO_SESSION_VISIBLE;
        }
    }

    private static int clamp(int value) {
        if (value < MIN_ORDER_PAGE_SIZE || value > MAX_ORDER_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        return value;
    }

    private static int integer(Object value, int fallback) {
        return value == null ? fallback : Integer.parseInt(value.toString());
    }

    private static String string(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static String trim(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public record TenantSystemSettings(
            String tenantId,
            GlobalPhoneDisplayPolicyMode phoneDisplayPolicy,
            int orderPageSize,
            Object updatedAt
    ) {
    }

    private record TenantSystemSettingsInput(GlobalPhoneDisplayPolicyMode phoneDisplayPolicy, int orderPageSize) {
        static TenantSystemSettingsInput from(Map<String, Object> payload) {
            if (payload == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST");
            }
            GlobalPhoneDisplayPolicyMode mode;
            try {
                mode = GlobalPhoneDisplayPolicyMode.valueOf(string(payload.get("phoneDisplayPolicy"), ""));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST");
            }
            return new TenantSystemSettingsInput(mode, clamp(integer(payload.get("orderPageSize"), DEFAULT_ORDER_PAGE_SIZE)));
        }
    }
}
