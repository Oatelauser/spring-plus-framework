package io.github.oatelauser.springplus.governor.idempotent;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单 JVM 幂等存储降级实现。
 * <p>
 * 过期键按访问和每 4096 次操作抽样清理；多实例部署必须使用 Redis 等共享存储。
 * <p>
 * 容量防护（CWE-400/770）：内存条目上限 {@value #DEFAULT_MAX_ENTRIES}（可经构造器调整），
 * 超限时拒绝写入（setIfAbsent 返回 false / put 抛 {@link IllegalStateException}）——
 * 攻击者刷不同参数指纹也无法令存储无界增长打爆堆内存；超限前会先做一轮过期清理尝试腾挪。
 * <p>
 * 使用红线：匿名/IP 主体（FingerprintKeyStrategy 降级链）在网关/出口 NAT 场景下
 * 不同用户共享同一主体指纹，只适合单机低风险场景；多租户/集群必须自定义
 * {@link IdempotentKeyStrategy} 加入租户维度并使用 {@link RedisIdempotentStore}。
 */
public class InMemoryIdempotentStore implements IdempotentStore {

    /** 默认容量上限：10 万条（防无界增长 OOM）。 */
    public static final int DEFAULT_MAX_ENTRIES = 100_000;

    private final int maxEntries;
    private final AtomicLong operations = new AtomicLong();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public InMemoryIdempotentStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryIdempotentStore(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        sweepIfNeeded();
        long expiresAt = expiresAt(ttl);
        Entry candidate = new Entry(value, expiresAt);
        try {
            return entries.compute(key, (ignored, current) -> {
                if (current == null || expired(current)) {
                    if (current == null && atCapacity()) {
                        throw new StoreFullException(maxEntries);
                    }
                    return candidate;
                }
                return current;
            }) == candidate;
        } catch (StoreFullException full) {
            // 满载等价"占位失败"：上游按重复提交语义拒绝，fail-closed 而非 500
            return false;
        }
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
        if (!entries.containsKey(key) && atCapacity()) {
            throw new StoreFullException(maxEntries);
        }
        entries.put(key, new Entry(value, expiresAt(ttl)));
    }

    @Override
    public void delete(String key) {
        entries.remove(key);
    }

    /** 当前条目数（含未过期的惰性条目），测试与监控用。 */
    public int size() {
        return entries.size();
    }

    private boolean atCapacity() {
        if (entries.size() < maxEntries) {
            return false;
        }
        // 满载先尝试清理过期腾挪，仍满则拒绝
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        return entries.size() >= maxEntries;
    }

    private void sweepIfNeeded() {
        if ((operations.incrementAndGet() & 4095L) == 0) {
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

    /**
     * 存储满载：setIfAbsent 场景等价"占位失败"（上游抛重复提交异常，fail-closed），
     * put 场景直接抛出。
     */
    static class StoreFullException extends RuntimeException {

        StoreFullException(int maxEntries) {
            super("InMemoryIdempotentStore 已达容量上限 " + maxEntries
                    + "，拒绝写入（防无界增长；请缩短 TTL、提高容量或切换 Redis 存储）");
        }
    }

    private record Entry(String value, long expiresAt) {
    }
}
