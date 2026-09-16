package io.github.oatelauser.springplus.boot.redis;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis缓存服务
 *
 * @author <a href="mailto:yangsheng1993812@gmail.com">Oatelauser</a>
 * @date 2024-10-12
 * @since 1.0
 */
public class RedisStringOperation {

    private final StringRedisTemplate redisTemplate;

    @SuppressWarnings("all")
    private final RedisScript<List> batchGetScript = RedisScript.of(new ClassPathResource("lua/bget.lua"), List.class);
    @SuppressWarnings("all")
    private final RedisScript<Long> batchDeleteScript = RedisScript.of(new ClassPathResource("lua/bdel.lua"), Long.class);
    private final RedisScript<Long> incrementScript = RedisScript.of(new ClassPathResource("lua/expire_increment.lua"), Long.class);

    public RedisStringOperation(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断是否有key
     *
     * @param key 键
     * @return true-存在
     */
    public boolean hasKey(String key) {
        Assert.hasText(key, "缓存key不能为空");
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取剩余的过期时间（单位s）
     *
     * @param key 键
     */
    public long getExpire(String key) {
        return this.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 获取剩余的过期时间
     *
     * @param key  键
     * @param unit 时间单位
     * @return 剩余的过期时间
     */
    public long getExpire(String key, TimeUnit unit) {
        Assert.hasText(key, "缓存key不能为空");
        return redisTemplate.getExpire(key, unit);
    }

    /**
     * 给缓存设置过期时间
     *
     * @param cacheKey 键
     * @param timeout  过期时间
     */
    public void expire(String cacheKey, int timeout, TimeUnit unit) {
        redisTemplate.expire(cacheKey, timeout, unit);
    }

    /**
     * 给缓存设置过期时间
     *
     * @param cacheKey 键
     * @param timeout  过期时间
     */
    public void expire(String cacheKey, int timeout) {
        this.expire(cacheKey, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置缓存
     *
     * @param key         键
     * @param value       数据
     * @param expireInSec 过期时间（单位s）
     */
    public void set(String key, String value, long expireInSec) {
        Assert.hasText(key, "缓存key不能为空");
        if (expireInSec < 0) {
            redisTemplate.opsForValue().set(key, value);
        } else {
            redisTemplate.opsForValue().set(key, value, expireInSec, TimeUnit.SECONDS);
        }
    }

    /**
     * 获取缓存数据
     *
     * @param key 键
     * @return 缓存数据
     */
    @Nullable
    public String get(String key) {
        Assert.hasText(key, "缓存key不能为空");
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 递增数据
     *
     * @param key         键
     * @param expireInSec 过期时间（单位s）
     * @return 递增之前的数
     */
    public Long incrementExpire(String key, long expireInSec) {
        Assert.hasText(key, "缓存key不能为空");
        return redisTemplate.execute(incrementScript, List.of(key), String.valueOf(expireInSec));
    }

    /**
     * 删除缓存数据
     *
     * @param key 键
     * @return true=删除成功
     */
    public boolean del(String key) {
        Assert.hasText(key, "缓存key不能为空");
        return redisTemplate.delete(key);
    }

    /**
     * 是否存在key
     *
     * @param key 键
     * @return true-存在
     */
    public boolean exists(String key) {
        Assert.hasText(key, "缓存key不能为空");
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 批量获取具有通配符类型key匹配的所有数据，例如：user:*
     *
     * @param key 键
     * @return Map<键, 值>
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> batchGet(String key) {
        Assert.hasText(key, "缓存key不能为空");
        List<List<String>> response = redisTemplate.execute(batchGetScript, List.of(), key);
        if (CollectionUtils.isEmpty(response)) {
            return Map.of();
        }

        Map<String, String> data = new HashMap<>(response.size());
        for (List<String> kv : response) {
            data.put(kv.get(0), kv.get(1));
        }
        return data;
    }

    /**
     * 批量删除具有通配符匹配的所有数据，例如：auth:token:*
     *
     * @param key 键
     * @return 删除个数
     */
    public Long batchDelete(String key) {
        Assert.hasText(key, "缓存key不能为空");
        // pattern 走 ARGV（KEYS 传 pattern 在 Cluster 下抛 CROSSSLOT），与 batchGet 对齐
        return redisTemplate.execute(batchDeleteScript, List.of(), key);
    }

    /**
     * 批量删除
     *
     * @param keys 键集合
     */
    public void batchDelete(List<String> keys) {
        Assert.notEmpty(keys, "缓存key列表不能为空");
        Assert.noNullElements(keys, "缓存key元素不能为空");
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            keys.forEach(key -> connection.keyCommands()
                    .del(redisTemplate.getStringSerializer().serialize(key)));
            return null;
        });
    }

}
