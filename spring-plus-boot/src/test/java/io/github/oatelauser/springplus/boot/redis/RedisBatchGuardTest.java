package io.github.oatelauser.springplus.boot.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V13 / CWE-400：批量通配护栏——纯通配拒绝、结果上限 fail-fast。
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2026-09-16
 * @since 1.1.0
 */
class RedisBatchGuardTest {

    // ───────────── pattern 作用域校验 ─────────────

    @Test
    void scopedPatternsPass() {
        assertDoesNotThrow(() -> RedisStringOperation.requireScopedPattern("user:*"));
        assertDoesNotThrow(() -> RedisStringOperation.requireScopedPattern("auth:token:*"));
        assertDoesNotThrow(() -> RedisStringOperation.requireScopedPattern("order*"));
        assertDoesNotThrow(() -> RedisStringOperation.requireScopedPattern("user:42"));
    }

    @Test
    void wildcardOnlyPatternsRejected() {
        for (String pattern : new String[]{"*", "*:*", "*:user", ":*", " * ", "***"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> RedisStringOperation.requireScopedPattern(pattern), pattern + " 应被拒绝");
        }
    }

    // ───────────── batchGet 结果上限 ─────────────

    @Test
    void oversizedBatchGetFailsFast() {
        // RedisStringOperation 构造需 StringRedisTemplate；护栏方法不触达 Redis，用可实例化路径验证
        RedisStringOperation operation = new RedisStringOperation(
                new org.springframework.data.redis.core.StringRedisTemplate());
        assertDoesNotThrow(() -> operation.requireWithinLimit(1000));
        assertThrows(IllegalStateException.class, () -> operation.requireWithinLimit(1001));

        operation.setMaxBatchGetResults(10);
        assertDoesNotThrow(() -> operation.requireWithinLimit(10));
        assertThrows(IllegalStateException.class, () -> operation.requireWithinLimit(11));
    }

}
