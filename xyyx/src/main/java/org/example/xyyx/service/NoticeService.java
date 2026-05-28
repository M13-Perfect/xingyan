package org.example.xyyx.service;

import org.example.xyyx.entity.Notice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class NoticeService {

    private final JdbcTemplate jdbcTemplate;

    public NoticeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createNotice(String operatorUsername, String title, String content, String level) {
        ensureAdmin(operatorUsername);
        String safeTitle = trimToNull(title);
        String safeContent = trimToNull(content);
        if (safeTitle == null || safeContent == null) {
            throw new IllegalArgumentException("title and content are required");
        }
        String safeLevel = normalizeLevel(level);
        jdbcTemplate.update(
                "INSERT INTO notice (title, content, level, created_by) VALUES (?, ?, ?, ?)",
                safeTitle,
                safeContent,
                safeLevel,
                operatorUsername
        );
    }

    public Map<String, Object> listNotices(String username, String status, int page, int size) {
        String safeUser = trimToNull(username);
        if (safeUser == null) {
            throw new IllegalArgumentException("username is required");
        }

        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(size, 50));
        int offset = (safePage - 1) * safeSize;
        String safeStatus = normalizeStatus(status);

        String statusSql = switch (safeStatus) {
            case "UNREAD" -> " AND IFNULL(s.is_read, 0) = 0 ";
            case "READ" -> " AND IFNULL(s.is_read, 0) = 1 ";
            default -> "";
        };

        String listSql = "SELECT n.id, n.title, n.content, n.level, n.created_by AS createdBy, n.created_at AS createdAt, " +
                "IFNULL(s.is_read, 0) AS isRead, s.read_at AS readAt " +
                "FROM notice n " +
                "LEFT JOIN notice_user_state s ON s.notice_id = n.id AND s.username = ? " +
                "WHERE n.is_deleted = 0 AND IFNULL(s.is_deleted, 0) = 0 " +
                statusSql +
                "ORDER BY n.created_at DESC LIMIT ? OFFSET ?";

        List<Notice> data = jdbcTemplate.query(
                listSql,
                (rs, rowNum) -> {
                    Notice notice = new Notice();
                    notice.setId(rs.getLong("id"));
                    notice.setTitle(rs.getString("title"));
                    notice.setContent(rs.getString("content"));
                    notice.setLevel(rs.getString("level"));
                    notice.setCreatedBy(rs.getString("createdBy"));
                    notice.setCreatedAt(rs.getTimestamp("createdAt").toLocalDateTime());
                    notice.setIsRead(rs.getInt("isRead"));
                    if (rs.getTimestamp("readAt") != null) {
                        notice.setReadAt(rs.getTimestamp("readAt").toLocalDateTime());
                    }
                    return notice;
                },
                safeUser,
                safeSize,
                offset
        );

        String countSql = "SELECT COUNT(*) FROM notice n " +
                "LEFT JOIN notice_user_state s ON s.notice_id = n.id AND s.username = ? " +
                "WHERE n.is_deleted = 0 AND IFNULL(s.is_deleted, 0) = 0 " +
                statusSql;

        Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, safeUser);
        int safeTotal = total == null ? 0 : total;
        int pages = safeTotal == 0 ? 1 : (int) Math.ceil((double) safeTotal / safeSize);
        return Map.of(
                "data", data,
                "total", safeTotal,
                "pages", pages
        );
    }

    public int getUnreadCount(String username) {
        String safeUser = trimToNull(username);
        if (safeUser == null) {
            throw new IllegalArgumentException("username is required");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notice n " +
                        "LEFT JOIN notice_user_state s ON s.notice_id = n.id AND s.username = ? " +
                        "WHERE n.is_deleted = 0 AND IFNULL(s.is_deleted, 0) = 0 AND IFNULL(s.is_read, 0) = 0",
                Integer.class,
                safeUser
        );
        return count == null ? 0 : count;
    }

    public int markRead(String username, List<Long> ids) {
        String safeUser = trimToNull(username);
        if (safeUser == null) {
            throw new IllegalArgumentException("username is required");
        }
        List<Long> validIds = loadValidNoticeIds(ids);
        if (validIds.isEmpty()) {
            return 0;
        }
        String upsertSql = "INSERT INTO notice_user_state (notice_id, username, is_read, read_at, is_deleted, deleted_at) " +
                "VALUES (?, ?, 1, NOW(), 0, NULL) " +
                "ON DUPLICATE KEY UPDATE is_read = 1, read_at = NOW(), is_deleted = 0, deleted_at = NULL";
        int affected = 0;
        for (Long id : validIds) {
            affected += jdbcTemplate.update(upsertSql, id, safeUser);
        }
        return affected;
    }

    public int deleteForUser(String username, List<Long> ids) {
        String safeUser = trimToNull(username);
        if (safeUser == null) {
            throw new IllegalArgumentException("username is required");
        }
        List<Long> validIds = loadValidNoticeIds(ids);
        if (validIds.isEmpty()) {
            return 0;
        }
        String upsertSql = "INSERT INTO notice_user_state (notice_id, username, is_read, read_at, is_deleted, deleted_at) " +
                "VALUES (?, ?, 0, NULL, 1, NOW()) " +
                "ON DUPLICATE KEY UPDATE is_deleted = 1, deleted_at = NOW()";
        int affected = 0;
        for (Long id : validIds) {
            affected += jdbcTemplate.update(upsertSql, id, safeUser);
        }
        return affected;
    }

    private void ensureAdmin(String operatorUsername) {
        String safeUser = trimToNull(operatorUsername);
        if (safeUser == null) {
            throw new IllegalArgumentException("missing admin identity");
        }
        List<String> roles = jdbcTemplate.queryForList(
                "SELECT role FROM user WHERE username = ?",
                String.class,
                safeUser
        );
        if (roles.isEmpty() || !"admin".equalsIgnoreCase(roles.get(0))) {
            throw new IllegalArgumentException("only admin can publish notices");
        }
    }

    private String normalizeStatus(String status) {
        String raw = trimToNull(status);
        if (raw == null) return "ALL";
        String upper = raw.toUpperCase(Locale.ROOT);
        if ("UNREAD".equals(upper) || "READ".equals(upper) || "ALL".equals(upper)) {
            return upper;
        }
        return "ALL";
    }

    private String normalizeLevel(String level) {
        String raw = trimToNull(level);
        if (raw == null) return "INFO";
        String upper = raw.toUpperCase(Locale.ROOT);
        if ("INFO".equals(upper) || "WARN".equals(upper) || "ALERT".equals(upper)) {
            return upper;
        }
        return "INFO";
    }

    private List<Long> loadValidNoticeIds(List<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id > 0) {
                    distinct.add(id);
                }
            }
        }
        if (distinct.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", distinct.stream().map(v -> "?").toList());
        String sql = "SELECT id FROM notice WHERE is_deleted = 0 AND id IN (" + placeholders + ")";
        List<Object> args = new ArrayList<>(distinct);
        List<Long> valid = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), args.toArray());
        return valid.stream().filter(Objects::nonNull).toList();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
