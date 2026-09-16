package io.github.oatelauser.springplus.governor.idempotent;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单 JVM 幂等存储降级实现。
 * <p>
 * ponytail: 过期键按访问和每 4096 次操作抽样清理；多实例部署必须使用 Redis 等共享存储。
 */
public class InMemoryIdempotentStore implements IdempotentStore {

    private long operations;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        sweepIfNeeded();
        long expiresAt = expiresAt(ttl);
        Entry candidate = new Entry(value, expiresAt);
        return entries.compute(key, (ignored, current) -> {
            if (current == null || expired(current)) {
                return candidate;
            }
            return current;
        }) == candidate;
    }

    @Override
    public String get(String key) {
        sweepIfNeeded();
        Entry entry = entries.get(key);
        if (entry == null || expired(entry)) {
            if (entry != null) {
                entries.remove(key, entry);
            }
            return null;
        }
        return entry.value();
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        sweepIfNeeded();
        entries.put(key, new Entry(value, expiresAt(ttl)));
    }

    @Override
    public void delete(String key) {
        entries.remove(key);
    }

    private void sweepIfNeeded() {
        if ((++operations & 4095) == 0) {
            long now = System.currentTimeMillis();
            entries.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
    }

    private static long expiresAt(Duration ttl) {
        return System.currentTimeMillis() + Math.max(1L, ttl.toMillis());
    }

    private static boolean expired(Entry entry) {
        return entry.expiresAt() <= System.currentTimeMillis();
    }

    private record Entry(String value, long expiresAt) {
    }
}
