package org.example.xyyx.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.example.xyyx.config.CookieBearerTokenResolver;
import org.example.xyyx.service.CasdoorAdminService;
import org.example.xyyx.service.CurrentUserService;
import org.example.xyyx.service.CurrentUserService.CurrentUser;
import org.example.xyyx.service.TokenDenylistService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final CurrentUserService currentUserService;
    private final TokenDenylistService tokenDenylistService;
    private final CasdoorAdminService casdoorAdminService;
    private final JwtDecoder jwtDecoder;

    public AuthController(CurrentUserService currentUserService,
                          TokenDenylistService tokenDenylistService,
                          CasdoorAdminService casdoorAdminService,
                          JwtDecoder jwtDecoder) {
        this.currentUserService = currentUserService;
        this.tokenDenylistService = tokenDenylistService;
        this.casdoorAdminService = casdoorAdminService;
        this.jwtDecoder = jwtDecoder;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = currentUserService.requireUser(jwt);
        return Map.of(
                "username", user.username(),
                "role", user.role(),
                "subject", jwt.getSubject(),
                "audience", jwt.getAudience()
        );
    }

    /**
     * 个人中心展示用的昵称/头像。前端已无 JS 可读 token，改由后端用 Cookie 里的身份服务端代取，避免 token 外泄。
     */
    @GetMapping("/me/account")
    public Map<String, String> myAccount(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = currentUserService.requireUser(jwt);
        Map<String, String> account = casdoorAdminService.getAccount(user.username());
        Map<String, String> result = new HashMap<>();
        String displayName = account.get("displayName");
        result.put("displayName", (displayName == null || displayName.isBlank()) ? user.username() : displayName);
        result.put("avatar", account.getOrDefault("avatar", ""));
        return result;
    }

    /**
     * 建立会话（Pattern B relay）：前端把 Casdoor 换来的 access token 交来，后端用 JWKS 校验它，
     * 确认对应本地用户后，写入 HttpOnly/Secure/SameSite Cookie（token 不再进入 JS）。校验失败绝不下发 Cookie。
     * 该端点在鉴权前放行（此刻还没有 Cookie），安全性由端点内部的 JWKS 校验 + 本地用户校验保证。
     */
    @PostMapping("/auth/session")
    public Map<String, Object> createSession(@RequestBody(required = false) Map<String, String> payload,
                                             HttpServletResponse response) {
        String accessToken = payload == null ? null : payload.get("accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ACCESS_TOKEN_REQUIRED");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(accessToken); // 验签 + 过期 + issuer/claims，失败即抛
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN");
        }
        CurrentUser user = currentUserService.requireUser(jwt); // 必须是本地已登记用户
        response.addHeader(HttpHeaders.SET_COOKIE, buildSessionCookie(accessToken, jwt.getExpiresAt()).toString());
        return Map.of("username", user.username(), "role", user.role());
    }

    /**
     * 登出即撤销：把当前 token 的会话指纹加入拒绝名单（TTL = token 剩余寿命），此后携带该 token 的请求一律 401。
     * 需要已认证（在 /api/** 之下），这样才能从 token 解析出会话指纹。前端仍可再调 Casdoor /api/logout 清其会话。
     */
    @PostMapping("/auth/logout")
    public Map<String, Object> logout(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        if (jwt != null) {
            long expiresAtEpochMs = jwt.getExpiresAt() != null
                    ? jwt.getExpiresAt().toEpochMilli()
                    : System.currentTimeMillis() + 3_600_000L;
            currentUserService.tokenRevocationKey(jwt)
                    .ifPresent(revocationKey -> tokenDenylistService.deny(revocationKey, expiresAtEpochMs));
        }
        // 让 HttpOnly Cookie 立即过期（Max-Age=0）。
        response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString());
        return Map.of("success", true);
    }

    private ResponseCookie buildSessionCookie(String value, Instant expiresAt) {
        long maxAgeSeconds = expiresAt != null
                ? Math.max(1, Duration.between(Instant.now(), expiresAt).getSeconds())
                : 3600;
        return baseCookie(value).maxAge(maxAgeSeconds).build();
    }

    private ResponseCookie expiredSessionCookie() {
        return baseCookie("").maxAge(0).build();
    }

    // Secure：浏览器在 http://localhost 也接受 Secure Cookie（本地开发 OK），生产走 HTTPS。SameSite=Lax 拦跨站 POST（CSRF 向量）。
    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(CookieBearerTokenResolver.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api");
    }
}
