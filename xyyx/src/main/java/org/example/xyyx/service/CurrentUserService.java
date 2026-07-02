package org.example.xyyx.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CurrentUserService {
    public static final String PHONE_VIEW_FULL = "PHONE_VIEW_FULL";
    public static final String PRIVACY_POLICY_MANAGE = "PRIVACY_POLICY_MANAGE";

    private final JdbcTemplate jdbcTemplate;

    public CurrentUserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CurrentUser requireUser(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Casdoor token");
        }

        String username = firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name")
        );
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Casdoor token missing username claim");
        }

        List<String> roles = jdbcTemplate.queryForList(
                "SELECT role FROM user WHERE username = ?",
                String.class,
                username
        );
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Casdoor user is not registered locally");
        }

        return new CurrentUser(username, roles.get(0));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public CurrentUser requireAdmin(Jwt jwt) {
        CurrentUser user = requireUser(jwt);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission: admin only");
        }
        return user;
    }

    public boolean hasPermission(CurrentUser user, String permission) {
        if (user == null || permission == null || permission.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " +
                        "FROM user u " +
                        "JOIN user_role ur ON ur.user_id = u.id AND ur.revoked_at IS NULL " +
                        "JOIN role_permission rp ON rp.role_id = ur.role_id " +
                        "WHERE u.username = ? AND rp.permission = ?",
                Integer.class,
                user.username(),
                permission
        );
        return count != null && count > 0;
    }

    public void requirePermission(CurrentUser user, String permission, String errorCode) {
        if (!hasPermission(user, permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, errorCode);
        }
    }

    public Long userId(CurrentUser user) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM user WHERE username = ?",
                Long.class,
                user.username()
        );
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Casdoor user is not registered locally");
        }
        return ids.get(0);
    }

    public Optional<String> loginSessionId(Jwt jwt) {
        if (jwt == null) {
            return Optional.empty();
        }
        String value = firstNonBlank(
                claim(jwt, "sid"),
                claim(jwt, "sessionId"),
                jwt.getId()
        );
        if (value != null) {
            return Optional.of(value);
        }
        String tokenValue = jwt.getTokenValue();
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("token-fingerprint:" + sha256(
                tokenValue + "|" + jwt.getIssuedAt() + "|" + jwt.getExpiresAt()
        ));
    }

    /**
     * 撤销名单专用的「单个 token」标识——必须按 token 粒度，绝不能用可能跨会话稳定的 sid/sessionId：
     * 否则登出把该 sid 加入名单后，同一 SSO 会话随后重新签发、携带同一 sid 的新 token 也会被误拒，
     * 造成"登出后一段时间内无法重新登录"。优先用每 token 唯一的 jti，退回 token 指纹。
     * 注意与 {@link #loginSessionId} 区分：后者用于手机号会话可见（希望跨 token 稳定），这里恰恰相反。
     */
    public Optional<String> tokenRevocationKey(Jwt jwt) {
        if (jwt == null) {
            return Optional.empty();
        }
        String jti = jwt.getId();
        if (jti != null && !jti.isBlank()) {
            return Optional.of("jti:" + jti);
        }
        String tokenValue = jwt.getTokenValue();
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("token-fingerprint:" + sha256(
                tokenValue + "|" + jwt.getIssuedAt() + "|" + jwt.getExpiresAt()
        ));
    }

    private String claim(Jwt jwt, String name) {
        Map<String, Object> claims = jwt.getClaims();
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
        }
    }

    public record CurrentUser(String username, String role) {
        public boolean isAdmin() {
            return "admin".equalsIgnoreCase(role);
        }
    }
}
