# spring-plus-redis-starter

Redis 能力域独立模块（ADR 0003，自 `spring-plus-boot-starter` 拆出）：`StringRedisTemplate` 增强工具，Lua 脚本保证原子性。

## 模块定位：小而精，不是 Redisson 替代品

本模块只做别处没有的四件事：**批量护栏**（防全库 SCAN/删除）、**Lua 原子操作门面**（批量/自增过期省往返）、**key 命名约定**、**Jackson 化 RedisTemplate**（Boot 不提供，生态最普遍的手写样板）。功能面刻意保持薄——薄是特性，护栏是灵魂；分布式锁、延迟队列等大件能力**不在本模块的版图内**（见下方选型指路）。

## 选型指路（不重复造轮子红线）

各能力域直接用成熟方案，不要在本模块或业务代码里重新实现：

| 需求 | 用什么 |
|---|---|
| 分布式锁 | Redisson `RLock`（token 比对释放 / 看门狗续期） |
| 限流 | Redisson `RRateLimiter` 或网关层能力 |
| 延迟队列 | Redisson `RDelayedQueue`（JDK `BlockingQueue` 语义）或真 MQ |
| 消息（Stream / Pub-Sub） | Spring Data Redis `StreamMessageListenerContainer` 或 MQ |
| 分布式 ID | 雪花 / Leaf / 号段模式；小场景拿本模块 `incrementExpire` 自拼日期段 |

**给本模块加新功能的立项门槛**（三条同时满足才立项）：Redisson 与 Spring 都没有该能力 + 手写必错（原子性/一致性陷阱）+ 本模块有护栏视角的增量。V13 批量通配护栏就是过了这三条才存在的。

## 能力清单

| 能力 | 入口 |
|---|---|
| String 原子操作 | `RedisStringOperation`：`incrementExpire`（自增+过期原子）、Lua 批量读 / 删 |
| 缓存工具 | `CacheUtils` / `KeyValue` |
| Jackson 化模板 | `jacksonRedisTemplate` Bean（`RedisTemplate<String, Object>`）/ `RedisJacksonTemplates` 工厂 |

## 坐标

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-redis-starter</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

无内部依赖。`spring-data-redis` 为 `provided`——容器存在 `StringRedisTemplate` 时自动装配 `RedisStringOperation`（`@ConditionalOnMissingBean`，同名 Bean 可替换），缺席时整体退避。

## 批量护栏（V13）

- `batchGet` / `batchDelete(pattern)` 的 pattern **必须包含实质前缀**（首个 `*` 前有字母数字，如 `user:*`）——纯通配（`*` / `*:*`）直接拒绝，防全库 SCAN / 删除
- `batchGet` 单次返回上限默认 **1000** 条（`setMaxBatchGetResults` 可调），超限 fail-fast 提示收紧 pattern

```java
redisStringOperation.incrementExpire("counter:login:" + userId, 1800);   // 秒
```

附带 3 个 Lua 脚本（`bdel` / `bget` / `expire_increment`）保证原子性。

## Jackson 化 RedisTemplate（1.1+）

Spring Boot 只自动装配 JDK 序列化的 `redisTemplate` 与 `stringRedisTemplate`——"Jackson 化模板"是 Spring 生态被复制粘贴最多的配置段。本模块补齐这个空档（过了立项三门槛：Redisson/Spring 都不提供 + 手写必错 + 家族约定增量）：

```java
// 按名注入（不接管 Boot 的 redisTemplate，存量 JDK 序列化数据零影响）
private final RedisTemplate<String, Object> jacksonRedisTemplate;

jacksonRedisTemplate.opsForValue().set("user:1", user);   // JSON 内嵌 @class
User back = (User) jacksonRedisTemplate.opsForValue().get("user:1");   // 读回即对象，无 ClassCastException
```

消掉的三类经典手写错误：

1. **忘嵌类型信息** → Object 值读回 `LinkedHashMap` 抛 `ClassCastException`：本模板以 `DefaultTyping.NON_FINAL` 在 JSON 内嵌 `@class`，round-trip 安全
2. **共享 mapper 被污染** → 把容器 `JsonMapper` 交给序列化器激活 default typing，连 HTTP 响应都开始输出 `@class`：本模板一律 `rebuild()` 副本配置，容器 mapper 分毫不动（有测试钉死）
3. **序列化器拼装错漏**：String key + Jackson value（hash 同构）一把装配

语义要点：

- 容器存在 `JsonMapper` Bean 时自动继承其约定（与 web 响应同源：日期格式等家族一致），无则回退自建
- **安全红线**：家族缺省类型校验器与 SDR 缺省一致（信任域 = 本应用写入的 Redis）。Redis 可被不可信方写入时，用 `RedisJacksonTemplates.jacksonValueSerializer(...)` 自行收紧 `PolymorphicTypeValidator` 装配——无约束多态反序列化面对不可信数据是任意代码执行风险
- 跨语言/省体积场景走 `StringRedisTemplate` + `RedisStringOperation`（String 通道），两个模板各司其职

## 已知注意事项

- 从 `spring-plus-boot-starter` 迁移而来：包名 `springplus.boot.redis` → `springplus.redis`，坐标由 boot 替换为本模块
- 批量 pattern 禁止拼接外部输入；护栏拒绝的纯通配不要试图绕过
