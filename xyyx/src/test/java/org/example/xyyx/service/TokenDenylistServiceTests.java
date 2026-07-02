package org.example.xyyx.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenDenylistServiceTests {

    @Test
    void deniesActiveSessionAndIgnoresExpiredOrBlank() {
        TokenDenylistService svc = new TokenDenylistService();
        long now = System.currentTimeMillis();

        assertFalse(svc.isDenied("s1"), "未登出的会话不应被拒绝");
        svc.deny("s1", now + 3_600_000L);
        assertTrue(svc.isDenied("s1"), "登出后应命中拒绝名单");

        // 过期时刻在过去：读取时应视为未拒绝并顺手清理。
        svc.deny("s2", now - 1_000L);
        assertFalse(svc.isDenied("s2"), "已过期的名单项不应再拒绝");

        // 空/空白 sessionId 不记录，也不误判。
        svc.deny(null, now + 1_000L);
        svc.deny("  ", now + 1_000L);
        assertFalse(svc.isDenied(null));
        assertFalse(svc.isDenied("  "));

        assertFalse(svc.isDenied("never-seen"), "从未登出的会话不应被拒绝");
    }
}
