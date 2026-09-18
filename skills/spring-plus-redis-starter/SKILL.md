---
name: spring-plus-redis-starter
description: spring-plus-framework 的 Redis 能力域使用约定（坐标 io.github.oatelauser:spring-plus-redis-starter）。覆盖：RedisStringOperation（incrementExpire 自增+过期原子操作、Lua 批量读/删、batchGet/batchDelete 通配护栏防全库 SCAN）、CacheUtils/KeyValue key 命名约定、jacksonRedisTemplate（RedisTemplate<String,Object> JSON 序列化，@class 内嵌类型信息防 ClassCastException）。模块定位小而精，不是 Redisson 替代品——分布式锁/延迟队列/限流不在版图内（选型指路：Redisson/Redisson RRateLimiter/MQ）。写 Redis 工具类、配 Jackson 序列化模板、排查批量删除被拒/读回 LinkedHashMap 报错时使用。
---

# spring-plus-redis-starter

这个 skill 直接给 AI 使用。本文件是导航与全局规则；API 细节与示例在 `references/`。

**模块定位：小而精，不是 Redisson 替代品。** 只做四件事：批量护栏（防全库 SCAN/删除）、Lua 原子操作门面、key 命名约定、Jackson 化 RedisTemplate（Boot 不提供的生态最普遍手写样板）。薄是特性，护栏是灵魂。

## 按需加载参考文档

| 任务涉及 | 加载 |
|---|---|
| RedisStringOperation 全 API、批量护栏规则、CacheUtils/KeyValue 命名约定 | [references/string-operations.md](references/string-operations.md) |
| jacksonRedisTemplate 装配、JSON 内嵌 @class、PolymorphicTypeValidator 安全红线 | [references/jackson-template.md](references/jackson-template.md) |

## 与 Spring Data Redis / 手写样板的关系（使用规则总纲）

本模块**不替换** Spring Data Redis——`RedisTemplate` / `StringRedisTemplate` 的全部官方 API 照常用；本模块只替代四类高频手写样板：

| 手写惯用法 | 本模块写法 |
|---|---|
| 网上复制粘贴的 Jackson `RedisTemplate` 配置段（易错三连：忘嵌类型/污染容器 mapper/拼装漏配） | 按名注入 `jacksonRedisTemplate`（`@class` 内嵌 round-trip 安全、容器 JsonMapper 取副本不被污染、一把装配） |
| 手写 `SCAN` 循环批量读/删（全库通配风险） | `batchGet(pattern)` / `batchDelete(pattern)`——护栏强制实质前缀、1000 条上限 |
| 两步 `increment` + `expire`（非原子，宕机留死键） | `incrementExpire(key, 秒)` Lua 原子 |
| 散拼 key 前缀字符串常量 | `KeyValue` 枚举集中声明 + `CacheUtils.getCacheKey(...)` |
| 想自己写分布式锁/延迟队列/限流 | **不做**——见下方选型指路表，直接用成熟方案 |

照常用边界：本模块 Bean 均带 `@ConditionalOnMissingBean`，同名 Bean 可整体替换；不引入 `spring-data-redis` 传递依赖（provided），由使用方按需引入。

## 选型指路（不重复造轮子红线）

| 需求 | 用什么 |
|---|---|
| 分布式锁 | Redisson `RLock`（token 比对释放 / 看门狗续期） |
| 限流 | Redisson `RRateLimiter` 或网关层 |
| 延迟队列 | Redisson `RDelayedQueue` 或真 MQ |
| 消息（Stream/Pub-Sub） | Spring Data Redis `StreamMessageListenerContainer` 或 MQ |
| 分布式 ID | 雪花/Leaf/号段；小场景拿 `incrementExpire` 自拼日期段 |

**给本模块加新功能的立项门槛**（三条同时满足）：Redisson 与 Spring 都没有该能力 + 手写必错（原子性/一致性陷阱）+ 本模块有护栏视角的增量。

## 红线

- 批量 pattern（`batchGet`/`batchDelete`）**必须含实质前缀**（首个 `*` 前有字母数字，如 `user:*`）——纯通配（`*`/`*:*`）直接拒绝；护栏拒绝的不要绕
- pattern **禁止拼接外部输入**
- Redis 可被不可信方写入时，必须用 `RedisJacksonTemplates.jacksonValueSerializer(...)` 自行收紧 `PolymorphicTypeValidator`——无约束多态反序列化面对不可信数据是任意代码执行风险（见 jackson-template.md）
- 本模块坐标替换原 `spring-plus-boot-starter` 传递的 Redis 工具（ADR 0003 拆分，包名 `springplus.boot.redis` → `springplus.redis`）

## 已知陷阱

- `spring-data-redis` 是 **provided**：容器存在 `StringRedisTemplate` Bean 时才自动装配 `RedisStringOperation`，缺席时整体退避（启动不报错，注入处才见分晓）
- `batchGet` 单次返回上限默认 **1000** 条（`setMaxBatchGetResults` 可调），超限 fail-fast——提示收紧 pattern，不是调大上限

## 文档同步约定

本 skill 的 API 断言以模块源码为唯一基准；模块行为变更时，模块 README 与本 skill（SKILL.md 及 references/）必须同步修改——只改一边视为未完成。
