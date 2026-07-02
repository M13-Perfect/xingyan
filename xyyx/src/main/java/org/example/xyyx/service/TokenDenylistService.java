package org.example.xyyx.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 登出即撤销。Casdoor 发的是无状态 JWT，Casdoor 端登出并不会让已签发的 token 在我们的资源服务器失效
 * （资源服务器只验签名 + 过期 + claims）。这里在本服务端维护一个「会话指纹 -> 过期时刻(epoch ms)」的
 * 拒绝名单：登出时把当前会话指纹加入名单，TTL = token 自身剩余寿命；过滤器对名单内的 token 直接判 401。
 * 会话指纹由 {@link CurrentUserService#loginSessionId} 统一计算（优先 sid/sessionId/jti，退回 token 指纹）。
 *
 * ponytail: 单 JVM 内存名单；多实例部署需换 Redis（与 InMemoryRateLimiter / PhoneRevealSessionService 同一约束）。
 */
@Service
public class TokenDenylistService {
    private final ConcurrentMap<String, Long> denied = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    public void deny(String sessionId, long expiresAtEpochMs) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        purgeExpired();
        // 同一会话多次登出：保留更晚的过期时刻即可。
        denied.merge(sessionId, expiresAtEpochMs, Math::max);
    }

    public boolean isDenied(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        Long expiresAt = denied.get(sessionId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= clock.millis()) {
            denied.remove(sessionId, expiresAt);
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        long now = clock.millis();
        denied.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
