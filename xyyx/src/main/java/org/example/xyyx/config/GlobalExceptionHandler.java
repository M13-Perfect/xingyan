package org.example.xyyx.config;

import jakarta.servlet.http.HttpServletRequest;
import org.example.xyyx.service.ApiException;
import org.example.xyyx.service.PhonePrivacyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e, HttpServletRequest request) {
        return error(e.status(), e.code(), message(e.code()), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String code = e.getReason() == null || e.getReason().isBlank() ? status.name() : e.getReason();
        return error(status, code, message(code), request);
    }

    @ExceptionHandler(PhonePrivacyException.class)
    public ResponseEntity<Map<String, Object>> handlePhone(PhonePrivacyException e, HttpServletRequest request) {
        HttpStatus status = "PHONE_PRIVACY_NOT_READY".equals(e.code()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return error(status, e.code(), message(e.code()), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception e, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", message("INTERNAL_SERVER_ERROR"), request);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message,
                "requestId", requestId(request)
        ));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }

    public static String message(String code) {
        return switch (code) {
            case "UNAUTHENTICATED" -> "登录状态已失效，请重新登录";
            case "PHONE_VIEW_DENIED", "PHONE_VIEW_SESSION_DENIED" -> "你没有权限查看完整手机号";
            case "SURVEY_ACCESS_DENIED" -> "无权访问该回访记录";
            case "PHONE_REVEAL_SESSION_POLICY_DISABLED" -> "本次登录显示完整手机号策略未开启";
            case "PHONE_SESSION_UNAVAILABLE" -> "手机号会话显示状态不可用，请先完成系统设置迁移";
            case "SURVEY_NOT_FOUND" -> "资源不存在";
            case "PHONE_PRIVACY_NOT_READY" -> "手机号隐私数据尚未完成迁移，请联系管理员处理";
            case "PHONE_REVEAL_TOO_FREQUENT", "PHONE_REVEAL_SESSION_TOO_FREQUENT" -> "操作过于频繁，请稍后再试";
            case "PHONE_DECRYPT_FAILED" -> "手机号解密失败，请联系管理员";
            case "AUDIT_LOG_UNAVAILABLE" -> "个人信息访问审计不可用，请联系管理员";
            default -> code;
        };
    }
}
