---
name: spring-plus-governor-starter
description: spring-plus-framework 的服务治理能力使用约定（坐标 io.github.oatelauser:spring-plus-governor-starter）。覆盖：@Idempotent 幂等提交（时间窗内同业务键仅首次生效）、@RepeatSubmit 防重复提交（参数指纹 + releaseOnFailure 释放语义）、IdempotentKeyStrategy 键策略（FingerprintKeyStrategy 参数指纹 / TokenKeyStrategy 登录主体 / 自定义租户维度）、IdempotentStore 双存储（RedisIdempotentStore 跨实例 / InMemoryIdempotentStore 兜底）、SpEL 键表达式（只读求值）。给接口加幂等/防重、对接幂等存储、排查重复提交误杀/漏放问题时使用。限流熔断暂不在本模块（规划中）。统一响应/异常在 spring-plus-web-starter。
---

# spring-plus-governor-starter

这个 skill 直接给 AI 使用。本文件是导航与全局规则；API 细节与示例在 `references/`。

**模块定位**：服务治理域。当前落地幂等提交与防重复提交两个能力；限流、熔断未来归此模块（尚不存在，别找）。

## 按需加载参考文档

| 任务涉及 | 加载 |
|---|---|
| @Idempotent / @RepeatSubmit 用法、窗口语义、SpEL 键表达式、releaseOnFailure | [references/idempotency.md](references/idempotency.md) |
| 键策略选择与自定义（租户维度）、存储选择（Redis/内存）、容量上限、集群部署要求 | [references/storage-and-strategy.md](references/storage-and-strategy.md) |

## 核心决策规则

1. "同一操作在时间窗内只算一次"（创建订单、发起支付）→ `@Idempotent`（默认窗口 60s）
2. "同一用户同一参数短窗内不许连点"（表单防抖）→ `@RepeatSubmit`（默认窗口 5s，防刷场景配 `releaseOnFailure = false`）
3. 键维度不满足（多租户/多主体）→ 自定义 `IdempotentKeyStrategy` 注册为 Bean，不改拦截器
4. 集群部署必须确保 Redis 存储（`spring-data-redis` 是 provided 不传递，需显式引入）

## 红线

- **集群 + 内存存储 = 幂等失效**：多实例各持一份 `InMemoryIdempotentStore`，同一请求打到不同实例会重复放行
- **匿名/IP 指纹在 NAT 下会互相误杀**（不同用户共享出口 IP）；多租户必须自定义键策略加租户/主体维度
- 幂等拦截是**编程式 AOP**（`MethodInterceptor` + `DefaultPointcutAdvisor`），对任意 Spring Bean 方法生效——不要试图再包一层 MVC 拦截器

## 已知陷阱

- 幂等拒绝默认渲染 `ClientStatus` 的重复提交语义（REPEAT_SUBMIT）——要定制响应就走 web 模块异常映射体系，不要在业务代码里 catch
- `InMemoryIdempotentStore` 容量默认 10 万条，满载 **fail-closed**（拒绝新 key 而非无界增长）
- `@Idempotent.express` / `@RepeatSubmit.spel` 的 SpEL 是**只读求值**（`T()` 类型引用 / `new` 构造 / 方法调用一律拒绝，V15）——表达式里别写调用逻辑

## 文档同步约定

本 skill 的 API 断言以模块源码为唯一基准；模块行为变更时，模块 README 与本 skill（SKILL.md 及 references/）必须同步修改——只改一边视为未完成。
