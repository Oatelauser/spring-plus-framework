# RedisStringOperation 与 key 命名约定

包：`io.github.oatelauser.springplus.redis`。装配条件：容器存在 `StringRedisTemplate` Bean（`spring-data-redis` provided，缺席整体退避）；`@ConditionalOnMissingBean`，同名 Bean 可整体替换。

## String 原子操作（Lua 保证）

```java
// 自增 + 过期一把原子完成（expireInSec 是秒数，不是 Duration）
redisStringOperation.incrementExpire("counter:login:" + userId, 1800);

// 单键常规操作（透传 StringRedisTemplate，统一入口）
redisStringOperation.set(key, value, 60);          // 秒级 TTL
redisStringOperation.get(key);
redisStringOperation.del(key);
redisStringOperation.expire(key, 60);
redisStringOperation.exists(key);
redisStringOperation.hasKey(key);
redisStringOperation.getExpire(key);               // 或 getExpire(key, TimeUnit unit)

// 批量（Lua 原子：bdel / bget / expire_increment 三个脚本随模块内置）
Map<String, String> hits = redisStringOperation.batchGet("user:*");
Long deleted           = redisStringOperation.batchDelete("user:*");
redisStringOperation.batchDelete(List.of("a", "b"));
```

## 批量护栏（V13）

- `batchGet(pattern)` / `batchDelete(pattern)` 的 pattern **必须含实质前缀**：首个 `*` 之前要有字母数字（如 `user:*` ✓，`*` / `*:*` ✗ 直接拒绝）——防全库 SCAN / 全库删除
- `batchGet` 单次返回上限默认 **1000** 条（`DEFAULT_MAX_BATCH_GET_RESULTS`；`setMaxBatchGetResults(int)` 可调），超限 fail-fast 报"请收紧 pattern"
- 超限的正确动作是**收紧 pattern 分批**，不是调大上限（护栏存在的意义就是防全量拉取打爆堆内存）
- pattern 禁止拼接外部输入

## key 命名约定（KeyValue + CacheUtils）

```java
public interface KeyValue {
    String getPrefix();     // 键前缀（业务域）
    String getName();       // 键名
    long getExpire();       // 默认 0（不过期）
    Class<?> getClazz();    // 值类型提示，默认 null
}

// 业务枚举实现 KeyValue，集中声明键契约
public enum BizKeys implements KeyValue {
    LOGIN_COUNTER("counter", "login", 1800),
    ;
    // getPrefix/getName/getExpire 实现
}

// 静态拼接（1~2 段）
String key = CacheUtils.getCacheKey(BizKeys.LOGIN_COUNTER, String.valueOf(userId));
```

规则：键前缀集中声明（KeyValue 枚举），不在业务代码里散拼字符串字面量；跨语言/省体积场景走这条 String 通道，对象缓存走 jacksonRedisTemplate。
