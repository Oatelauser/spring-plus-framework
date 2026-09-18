---
name: spring-plus-redis-starter
description: 在已引入 io.github.oatelauser:spring-plus-redis-starter 的项目中进行 Redis 字符串原子操作（自增+过期 / Lua 批量读删）或缓存工具相关代码的编写或排障时优先使用。本 skill 定义 RedisStringOperation / CacheUtils 的使用约定与批量通配护栏。
---

# spring-plus-redis-starter

这个 skill 直接给 AI 使用。

## 前置假设

- 项目已引入 `io.github.oatelauser:spring-plus-redis-starter`（无内部依赖；`spring-data-redis` 为 provided，容器存在 `StringRedisTemplate` 时自动装配）

## 模块定位

Redis 能力域（ADR 0003 自 boot 拆出）：`StringRedisTemplate` 增强——Lua 脚本原子操作、批量读删护栏、缓存工具。1.1.0 前位于 `spring-plus-boot-starter` 的 `boot.redis` 包，已迁移。

**小而精是刻意设计**：本模块只做"护栏 + 原子门面 + key 命名约定"，**不是 Redisson 替代品**。

## 不要这样做

- **不要在本模块或业务代码里重复实现成熟方案**（选型指路）：分布式锁 → Redisson `RLock`；限流 → `RRateLimiter` 或网关层；延迟队列 → Redisson `RDelayedQueue` 或 MQ；消息 → Spring `StreamMessageListenerContainer` 或 MQ；分布式 ID → 雪花 / Leaf / 号段
- 不要在业务代码里直接操作 `StringRedisTemplate` 完成本模块已封装的原子操作
- 不要手写 Jackson 化 RedisTemplate 配置类（类型内嵌遗漏/mapper 污染两类坑，用 `jacksonRedisTemplate`）；Redis 可被不可信方写入时必须收紧 `PolymorphicTypeValidator`
- 批量 pattern 禁止拼接外部输入；护栏拒绝的纯通配不要绕过
- 给本模块加新功能须同时满足三条：Redisson/Spring 都没有 + 手写必错 + 有护栏视角增量，否则不立项

## 优先复用的公开类型

Redis 工具（`io.github.oatelauser.springplus.redis`）：

- `RedisStringOperation`（`incrementAndExpire` 自增+过期原子 / `batchGet` / `batchDelete`，Lua 驱动）
- `CacheUtils` / `KeyValue`
- `jacksonRedisTemplate` Bean（`RedisTemplate<String, Object>`，按名注入）与工厂 `RedisJacksonTemplates`：JSON 内嵌 `@class` 的对象缓存通道，容器 `JsonMapper` 取副本不被污染

## 决策规则

1. 字符串原子操作（自增+过期等）一律用 `RedisStringOperation`，不要手写 Lua
2. 批量读删的 pattern 必须含实质前缀（如 `user:*`）；纯通配被护栏拒绝
3. 需要替换默认行为时注册同名 `RedisStringOperation` Bean（`@ConditionalOnMissingBean` 退避）
4. 对象缓存（存取 POJO）用 `jacksonRedisTemplate` 按名注入，不要手写 Jackson 序列化模板配置；跨语言/省体积走 `StringRedisTemplate` + `RedisStringOperation`

## 已知注意事项

- `batchGet` 单次上限默认 1000 条（`setMaxBatchGetResults` 可调），超限 fail-fast
- 未引入 spring-data-redis 的项目自动配置整体退避，不会报错
