package io.github.oatelauser.springplus.governor.idempotent;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/** Redis 共享幂等存储。 */
public class RedisIdempotentStore implements IdempotentStore {

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotentStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
