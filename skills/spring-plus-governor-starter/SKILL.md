---
name: spring-plus-governor-starter
description: 在已引入 io.github.oatelauser:spring-plus-governor-starter 的项目中处理幂等提交、防重复提交、接口防重场景时优先使用。本 skill 定义 @Idempotent 与 @RepeatSubmit 的使用边界、键策略扩展、Redis/内存双存储的适用条件，以及未来限流熔断能力的归属。
---

# spring-plus-governor-starter

这个 skill 直接给 AI 使用。

## 前置假设

- 项目已引入 `io.github.oatelauser:spring-plus-governor-starter`（会传递引入 `spring-plus-web-starter`）

## 模块定位

服务治理域。当前提供幂等提交（`@Idempotent`）与防重复提交（`@RepeatSubmit`）；限流、熔断、降级等治理能力未来也归此模块。

幂等不是 web 协议能力——不要去 web 模块找它；也不要把限流/熔断的新实现放到 web 或 boot。

## 优先复用的公开类型

- `@Idempotent` / `@RepeatSubmit`（`io.github.oatelauser.springplus.governor.annotation`）
- `IdempotentKeyStrategy` / `FingerprintKeyStrategy` / `KeyStrategyResolver`（`governor.idempotent`）
- `IdempotentStore` / `RedisIdempotentStore` / `InMemoryIdempotentStore`
- `IdempotentInterceptor` / `RepeatSubmitInterceptor` / `IdempotentPointcuts`

## 决策规则

1. 写操作防双击/防重复下单 → `@RepeatSubmit`（时间窗内同参数指纹拒绝）
2. 业务幂等（如支付回调、订单创建的去重）→ `@Idempotent`（业务键时间窗）
3. 键规则不满足需求 → 实现 `IdempotentKeyStrategy` 注册为 Bean，`KeyStrategyResolver` 自动收集；不要复制拦截器改逻辑
4. 集群部署必须确认 classpath 有 spring-data-redis（跨实例幂等）；单实例内存存储即可

## 使用规则

```java
@Idempotent
@PostMapping("/order/create")
public SimpleResponse<Void> create(@Valid @RequestBody CreateOrderCmd cmd) { ... }

@RepeatSubmit(interval = 5, timeUnit = TimeUnit.SECONDS)
@PostMapping("/pay")
public SimpleResponse<Void> pay(@Valid @RequestBody PayCmd cmd) { ... }
```

## 不要这样做

- 不要在 Controller 层手写"查重表"实现幂等（注解 + 存储已覆盖）
- 不要假设注解对非 Spring Bean 方法生效（AOP 织入，同类内部调用不走代理）
- 不要在幂等拦截器里做耗时业务逻辑

## 已知注意事项

- **主体维度红线**：匿名/IP 主体（FingerprintKeyStrategy 降级链）在网关/出口 NAT 下不同用户共享指纹——多租户/集群必须自定义 `IdempotentKeyStrategy` 加租户维度并使用 Redis 存储
- 内存存储默认容量 10 万条（构造器可调），满载后 fail-closed 拒绝新 key（防无界增长），不是 bug

- 织入方式是编程式 AOP（MethodInterceptor + Advisor），与 MVC 拦截器无关
- Redis 存储探测按类名反射：无 Redis 依赖时模块可安全加载，自动回落内存存储
- 幂等拒绝响应走全局异常体系的 `ClientStatus` 重复提交语义，可用异常注解自定义
