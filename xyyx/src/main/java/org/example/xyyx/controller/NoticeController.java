package org.example.xyyx.controller;

import org.example.xyyx.service.NoticeService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
@CrossOrigin
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PostMapping
    public Map<String, Object> createNotice(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            noticeService.createNotice(
                    stringValue(payload.get("operatorUsername")),
                    stringValue(payload.get("title")),
                    stringValue(payload.get("content")),
                    stringValue(payload.get("level"))
            );
            result.put("success", true);
            result.put("message", "notice published");
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "failed to publish notice");
        }
        return result;
    }

    @GetMapping
    public Map<String, Object> listNotices(
            @RequestParam String username,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return noticeService.listNotices(username, status, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(@RequestParam String username) {
        return Map.of("count", noticeService.getUnreadCount(username));
    }

    @PutMapping("/read")
    public Map<String, Object> markRead(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            int affected = noticeService.markRead(
                    stringValue(payload.get("username")),
                    parseLongIds(payload.get("ids"))
            );
            result.put("success", true);
            result.put("affected", affected);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "failed to mark as read");
        }
        return result;
    }

    @DeleteMapping
    public Map<String, Object> deleteNotices(@RequestBody Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            int affected = noticeService.deleteForUser(
                    stringValue(payload.get("username")),
                    parseLongIds(payload.get("ids"))
            );
            result.put("success", true);
            result.put("affected", affected);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "failed to delete notices");
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private List<Long> parseLongIds(Object rawIds) {
        List<Long> ids = new ArrayList<>();
        if (!(rawIds instanceof List<?> list)) {
            return ids;
        }
        for (Object item : list) {
            if (item instanceof Number number) {
                ids.add(number.longValue());
                continue;
            }
            if (item != null) {
                try {
                    ids.add(Long.parseLong(item.toString()));
                } catch (NumberFormatException ignored) {
                    // ignore invalid id
                }
            }
        }
        return ids;
    }
}
