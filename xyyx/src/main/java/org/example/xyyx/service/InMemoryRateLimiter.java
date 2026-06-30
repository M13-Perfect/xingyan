package org.example.xyyx.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InMemoryRateLimiter {
    private final ConcurrentMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    public void require(String key, int limit, long windowMillis, String code) {
        require(key, limit, windowMillis, 1, code);
    }

    public void require(String key, int limit, long windowMillis, int weight, String code) {
        if (weight <= 0) {
            return;
        }
        long now = clock.millis();
        Deque<Long> bucket = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            long cutoff = now - windowMillis;
            while (!bucket.isEmpty() && bucket.peekFirst() <= cutoff) {
                bucket.removeFirst();
            }
            if (bucket.size() + weight > limit) {
                throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, code);
            }
            // ponytail: single-JVM limiter; Redis/central limiter is required for multi-instance deployment.
            for (int i = 0; i < weight; i++) {
                bucket.addLast(now);
            }
        }
    }
}
