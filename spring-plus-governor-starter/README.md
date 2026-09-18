# spring-plus-governor-starter

服务治理域：幂等提交与防重复提交。限流、熔断等治理能力未来也归此模块。

## 能力清单

| 能力 | 入口 |
|---|---|
| 幂等提交 | `@Idempotent`：同一业务键在时间窗内仅首次生效 |
| 防重复提交 | `@RepeatSubmit`：同一参数指纹在时间窗内拒绝重复请求 |
| 键策略 | `FingerprintKeyStrategy` / `IdempotentKeyStrategy` / `KeyStrategyResolver`（自定义策略注册为 Bean 即生效） |
| 存储 | `RedisIdempotentStore`（classpath 有 Redis 时自动）/ `InMemoryIdempotentStore`（兜底） |

## 坐标

```xml
<dependency>
    <groupId>io.github.oatelauser</groupId>
    <artifactId>spring-plus-governor-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

依赖 `spring-plus-web-starter`（传递引入）。

## 用法

```java
@Idempotent
@PostMapping("/order/create")
public SimpleResponse<Void> create(@Valid @RequestBody CreateOrderCmd cmd) { ... }

@RepeatSubmit(interval = 5, timeUnit = TimeUnit.SECONDS)
@PostMapping("/pay")
public SimpleResponse<Void> pay(@Valid @RequestBody PayCmd cmd) { ... }
```

## 机制

- 织入方式为**编程式 AOP**（`MethodInterceptor` + `DefaultPointcutAdvisor`），不依赖 MVC 拦截器注册，对任意 Spring Bean 方法生效
- 存储探测：classpath 存在 `StringRedisTemplate` 时使用 Redis 存储（跨实例幂等），否则回落内存存储（单实例）——探测只在方法体内按类名反射，无 Redis 依赖时本模块仍可安全加载
- 自定义键策略：实现 `IdempotentKeyStrategy` 并注册为 Spring Bean，`KeyStrategyResolver` 自动收集

## 与旧项目的关系

幂等能力源自旧 `spring-web-framework` 的 `idempotent` 包，按"服务治理域"归属迁移至本模块（原 web 模块空壳 governor 的 pom 中本就预留了 resilience4j 限流/熔断坐标，本模块是该规划的落地起点）。AOP 装配从原 `SpringWebFrameworkAutoConfiguration` 迁至 `SpringPlusGovernorAutoConfiguration`。

## 已知注意事项

- 匿名/IP 主体只适合单机低风险场景（NAT 下不同用户共享指纹会互相误杀）；多租户必须自定义键策略加租户维度，集群必须 Redis
- `InMemoryIdempotentStore` 默认容量上限 10 万条（构造器可调），满载 fail-closed 拒绝新 key（防无界增长）

- 内存存储仅适用单实例部署；集群部署必须引入 spring-data-redis（provided 依赖不传递）
- 幂等拒绝的响应码默认走 `ClientStatus` 的重复提交语义，可经全局异常体系自定义映射
